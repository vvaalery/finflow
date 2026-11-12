package com.finance.graphqlapi.service;

import com.finance.backend.entity.*;
import com.finance.backend.repository.*;
import com.finance.paymentservice.service.PaymentValidationService;
import com.finance.kafka.event.PaymentRequestCreatedEvent;
import com.finance.graphqlapi.dto.GraphQLInput;
import com.finance.graphqlapi.dto.GraphQLView;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.finance.kafka.outbox.OutboxEventService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GraphQLFinanceService {
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final PaymentCalendarRepository paymentRepository;
    private final PaymentRequestRepository paymentRequestRepository;
    private final CashFlowForecastRepository forecastRepository;
    private final PaymentValidationService paymentValidationService;
    private final OutboxEventService outboxEventService;

    public List<GraphQLView.UserView> users() { return userRepository.findAll().stream().map(this::user).toList(); }
    public GraphQLView.UserView user(UUID id) { return userRepository.findById(id).map(this::user).orElse(null); }
    public List<GraphQLView.CompanyView> companies() { return companyRepository.findAll().stream().map(this::company).toList(); }
    public GraphQLView.CompanyView company(UUID id) { return companyRepository.findById(id).map(this::company).orElse(null); }
    public List<GraphQLView.AccountView> accounts(UUID companyId) { return accountRepository.findByCompanyId(companyId).stream().map(this::account).toList(); }
    public GraphQLView.AccountView account(UUID id) { return accountRepository.findById(id).map(this::account).orElse(null); }

    public List<GraphQLView.TransactionView> transactions(UUID accountId, GraphQLInput.TransactionFilter filter, GraphQLInput.TransactionSort sort, Integer limit, Integer offset) {
        List<Transaction> list = transactionRepository.findByAccountIdOrderByDateDesc(accountId);
        if (filter != null) list = list.stream().filter(t -> filter.type() == null || filter.type().equalsIgnoreCase(t.getTransactionType()))
                .filter(t -> filter.category() == null || filter.category().equalsIgnoreCase(t.getCategory()))
                .filter(t -> filter.from() == null || !t.getDate().isBefore(filter.from()))
                .filter(t -> filter.to() == null || !t.getDate().isAfter(filter.to()))
                .filter(t -> filter.minAmount() == null || t.getAmount().compareTo(filter.minAmount()) >= 0)
                .filter(t -> filter.maxAmount() == null || t.getAmount().compareTo(filter.maxAmount()) <= 0).toList();
        if (sort != null && sort.field() != null) {
            Comparator<Transaction> c = switch (sort.field()) {
                case "amount" -> Comparator.comparing(Transaction::getAmount);
                case "date" -> Comparator.comparing(Transaction::getDate);
                case "category" -> Comparator.comparing(t -> Optional.ofNullable(t.getCategory()).orElse(""));
                default -> Comparator.comparing(Transaction::getDate);
            };
            if ("DESC".equalsIgnoreCase(sort.direction())) c = c.reversed();
            list = list.stream().sorted(c).toList();
        }
        int from = Math.max(0, offset == null ? 0 : offset);
        int to = Math.min(list.size(), from + Math.max(1, limit == null ? 100 : limit));
        return from >= list.size() ? List.of() : list.subList(from, to).stream().map(this::transaction).toList();
    }

    public GraphQLView.TransactionView transaction(UUID id) { return transactionRepository.findById(id).map(this::transaction).orElse(null); }
    public List<GraphQLView.PaymentView> payments(UUID companyId) { return paymentRepository.findByCompanyId(companyId).stream().map(this::payment).toList(); }
    public List<GraphQLView.PaymentRequestView> paymentRequests() { return paymentRequestRepository.findAllByOrderByDateDesc().stream().map(this::paymentRequest).toList(); }
    public GraphQLView.PaymentRequestView paymentRequest(UUID id) { return paymentRequestRepository.findById(id).map(this::paymentRequest).orElse(null); }

    @Transactional
    public GraphQLView.MutationResult createPaymentRequest(GraphQLInput.PaymentRequestInput input) {
        paymentValidationService.validateAccountOwnership(input.accountId(), input.companyId());
        PaymentRequest p=new PaymentRequest(); p.setAccount(accountRepository.findById(input.accountId()).orElseThrow()); p.setTitle(input.title()); p.setAmount(input.amount()); p.setDate(input.date()); p.setStatus("PENDING"); p.setRequester(input.requester()); p.setCounterparty(input.counterparty()); p.setCategory(input.category()); p.setDescription(input.description());
        PaymentRequest saved = paymentRequestRepository.save(p);
        outboxEventService.enqueue(new PaymentRequestCreatedEvent(
                saved.getId(),
                saved.getTitle(),
                saved.getAmount(),
                saved.getStatus(),
                saved.getDate()
        ));
        return result(saved.getId(), "Платёжная заявка создана");
    }
    public List<GraphQLView.ForecastView> forecasts(UUID companyId) { return forecastRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream().map(this::forecast).toList(); }

    public GraphQLView.AggregateView aggregate(UUID companyId, LocalDate from, LocalDate to) {
        List<Transaction> tx = transactionRepository.findByAccountCompanyIdAndDateBetween(companyId, from == null ? LocalDate.of(2000,1,1) : from, to == null ? LocalDate.now() : to);
        BigDecimal income = tx.stream().filter(t -> "INCOME".equalsIgnoreCase(t.getTransactionType())).map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expense = tx.stream().filter(t -> "EXPENSE".equalsIgnoreCase(t.getTransactionType())).map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = tx.isEmpty() ? BigDecimal.ZERO : tx.stream().map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(tx.size()), 2, java.math.RoundingMode.HALF_UP);
        return new GraphQLView.AggregateView(income, expense, income.subtract(expense), tx.size(), avg);
    }

    @Transactional
    public GraphQLView.MutationResult createCompany(GraphQLInput.CompanyInput input, UUID ownerId) {
        User owner = userRepository.findById(ownerId).orElseThrow(() -> new IllegalArgumentException("Владелец не найден"));
        Company c = new Company(); c.setName(input.name()); c.setTaxId(input.taxId()); c.setAddress(input.address()); c.setPhone(input.phone()); c.setDescription(input.description()); c.setOwner(owner); c.setStatus("ACTIVE");
        return result(companyRepository.save(c).getId(), "Компания создана");
    }

    @Transactional
    public GraphQLView.MutationResult updateCompany(UUID id, GraphQLInput.CompanyInput input) {
        Company c = companyRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Компания не найдена"));
        c.setName(input.name()); c.setTaxId(input.taxId()); c.setAddress(input.address()); c.setPhone(input.phone()); c.setDescription(input.description());
        companyRepository.save(c); return result(id, "Компания обновлена");
    }

    @Transactional
    public GraphQLView.MutationResult deleteCompany(UUID id) { if (!companyRepository.existsById(id)) throw new NoSuchElementException("Компания не найдена"); companyRepository.deleteById(id); return result(id, "Компания удалена"); }

    @Transactional
    public GraphQLView.MutationResult createAccount(GraphQLInput.AccountInput input) {
        Company c = companyRepository.findById(input.companyId()).orElseThrow(() -> new NoSuchElementException("Компания не найдена"));
        Account a = new Account(); a.setCompany(c); a.setAccountName(input.accountName()); a.setAccountType(input.accountType().toUpperCase()); a.setCurrency(input.currency().toUpperCase()); a.setBalance(input.balance()); a.setIsActive(true);
        return result(accountRepository.save(a).getId(), "Счёт создан");
    }

    @Transactional
    public GraphQLView.MutationResult updateAccount(UUID id, GraphQLInput.AccountInput input) {
        Account a = accountRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Счёт не найден"));
        a.setAccountName(input.accountName()); a.setAccountType(input.accountType().toUpperCase()); a.setCurrency(input.currency().toUpperCase()); a.setBalance(input.balance());
        accountRepository.save(a); return result(id, "Счёт обновлён");
    }

    @Transactional
    public GraphQLView.MutationResult deleteAccount(UUID id) { if (!accountRepository.existsById(id)) throw new NoSuchElementException("Счёт не найден"); accountRepository.deleteById(id); return result(id, "Счёт удалён"); }

    @Transactional
    public GraphQLView.MutationResult createTransaction(GraphQLInput.TransactionInput input) {
        Account a = accountRepository.findById(input.accountId()).orElseThrow(() -> new NoSuchElementException("Счёт не найден"));
        Transaction t = new Transaction(); t.setAccount(a); t.setTransactionType(input.transactionType().toUpperCase()); t.setAmount(input.amount()); t.setDate(input.date()); t.setDescription(input.description()); t.setCategory(input.category()); t.setCounterparty(input.counterparty()); t.setCfoId(input.cfoId());
        if ("INCOME".equals(t.getTransactionType())) a.setBalance(a.getBalance().add(t.getAmount())); else if ("EXPENSE".equals(t.getTransactionType())) a.setBalance(a.getBalance().subtract(t.getAmount())); else throw new IllegalArgumentException("transactionType должен быть INCOME или EXPENSE");
        accountRepository.save(a); return result(transactionRepository.save(t).getId(), "Транзакция создана");
    }

    @Transactional
    public GraphQLView.MutationResult updateTransaction(UUID id, GraphQLInput.TransactionInput input) {
        Transaction t = transactionRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Транзакция не найдена"));
        Account oldAccount = t.getAccount();
        if ("INCOME".equals(t.getTransactionType())) oldAccount.setBalance(oldAccount.getBalance().subtract(t.getAmount()));
        if ("EXPENSE".equals(t.getTransactionType())) oldAccount.setBalance(oldAccount.getBalance().add(t.getAmount()));
        Account newAccount = accountRepository.findById(input.accountId()).orElseThrow(() -> new NoSuchElementException("Новый счёт не найден"));
        t.setAccount(newAccount); t.setTransactionType(input.transactionType().toUpperCase()); t.setAmount(input.amount()); t.setDate(input.date()); t.setDescription(input.description()); t.setCategory(input.category()); t.setCounterparty(input.counterparty()); t.setCfoId(input.cfoId());
        if ("INCOME".equals(t.getTransactionType())) newAccount.setBalance(newAccount.getBalance().add(t.getAmount())); else if ("EXPENSE".equals(t.getTransactionType())) newAccount.setBalance(newAccount.getBalance().subtract(t.getAmount())); else throw new IllegalArgumentException("transactionType должен быть INCOME или EXPENSE");
        accountRepository.save(oldAccount); accountRepository.save(newAccount); transactionRepository.save(t); return result(id, "Транзакция обновлена");
    }

    @Transactional
    public GraphQLView.MutationResult deleteTransaction(UUID id) { Transaction t = transactionRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Транзакция не найдена")); Account a=t.getAccount(); if ("INCOME".equals(t.getTransactionType())) a.setBalance(a.getBalance().subtract(t.getAmount())); else if ("EXPENSE".equals(t.getTransactionType())) a.setBalance(a.getBalance().add(t.getAmount())); accountRepository.save(a); transactionRepository.delete(t); return result(id, "Транзакция удалена"); }

    @Transactional
    public GraphQLView.MutationResult createPayment(GraphQLInput.PaymentInput input) { Company c=companyRepository.findById(input.companyId()).orElseThrow(() -> new NoSuchElementException("Компания не найдена")); PaymentCalendar p=new PaymentCalendar(); p.setCompany(c); p.setTitle(input.title()); p.setDescription(input.description()); p.setAmount(input.amount()); p.setDueDate(input.dueDate()); p.setStatus(input.status()==null?"PENDING":input.status()); p.setCategory(input.category()); p.setCounterparty(input.counterparty()); return result(paymentRepository.save(p).getId(), "Платёж добавлен"); }
    @Transactional
    public GraphQLView.MutationResult updatePayment(UUID id, GraphQLInput.PaymentInput input) { PaymentCalendar p=paymentRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Платёж не найден")); p.setTitle(input.title()); p.setDescription(input.description()); p.setAmount(input.amount()); p.setDueDate(input.dueDate()); p.setStatus(input.status()); p.setCategory(input.category()); p.setCounterparty(input.counterparty()); paymentRepository.save(p); return result(id,"Платёж обновлён"); }
    @Transactional
    public GraphQLView.MutationResult deletePayment(UUID id) { if(!paymentRepository.existsById(id)) throw new NoSuchElementException("Платёж не найден"); paymentRepository.deleteById(id); return result(id,"Платёж удалён"); }

    private GraphQLView.MutationResult result(UUID id,String msg){return new GraphQLView.MutationResult(true,msg,id.toString());}
    private GraphQLView.UserView user(User u){return new GraphQLView.UserView(u.getId(),u.getEmail(),u.getFullName(),new GraphQLView.RoleView(u.getRole().getId(),u.getRole().getName()),Boolean.TRUE.equals(u.getIsActive()),u.getCreatedAt());}
    private GraphQLView.CompanyView company(Company c){return new GraphQLView.CompanyView(c.getId(),c.getName(),c.getTaxId(),c.getAddress(),c.getPhone(),c.getDescription(),c.getWebsiteUrl(),c.getLogoUrl(),c.getStatus(),c.getOwner().getId(),c.getCreatedAt());}
    private GraphQLView.AccountView account(Account a){return new GraphQLView.AccountView(a.getId(),a.getCompany().getId(),a.getAccountName(),a.getAccountType(),a.getCurrency(),a.getBalance(),Boolean.TRUE.equals(a.getIsActive()),a.getCreatedAt());}
    private GraphQLView.TransactionView transaction(Transaction t){return new GraphQLView.TransactionView(t.getId(),t.getAccount().getId(),t.getTransactionType(),t.getAmount(),t.getDate(),t.getDescription(),t.getCategory(),t.getCounterparty(),t.getCfoId(),t.getCreatedAt());}
    private GraphQLView.PaymentView payment(PaymentCalendar p){return new GraphQLView.PaymentView(p.getId(),p.getCompany().getId(),p.getTitle(),p.getDescription(),p.getAmount(),p.getDueDate(),p.getStatus(),p.getCategory(),p.getCounterparty(),p.getCreatedBy()==null?null:p.getCreatedBy().getId(),p.getCreatedAt());}
    private GraphQLView.PaymentRequestView paymentRequest(PaymentRequest p){return new GraphQLView.PaymentRequestView(p.getId(),p.getTitle(),p.getAmount(),p.getDate(),p.getStatus(),p.getRequester(),p.getCounterparty(),p.getCategory(),p.getDescription());}
    private GraphQLView.ForecastView forecast(CashFlowForecast f){return new GraphQLView.ForecastView(f.getId(),f.getCompany().getId(),f.getForecastPeriodStart(),f.getForecastPeriodEnd(),f.getTotalProjectedInflow(),f.getTotalProjectedOutflow(),f.getNetCashFlow(),f.getStrategyUsed(),f.getCreatedAt());}
}
