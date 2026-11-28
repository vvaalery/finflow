package com.finance.internal.service;

import com.finance.backend.entity.Account;
import com.finance.backend.entity.Company;
import com.finance.backend.entity.Transaction;
import com.finance.backend.entity.User;
import com.finance.backend.repository.AccountRepository;
import com.finance.backend.repository.CompanyRepository;
import com.finance.backend.repository.TransactionRepository;
import com.finance.backend.repository.UserRepository;
import com.finance.backend.service.CompanyDirectoryService;
import com.finance.internal.dto.GraphQLInput.*;
import com.finance.internal.dto.GraphQLView.*;
import com.finance.internal.security.AccessPolicy;
import com.finance.kafka.event.EventTypes;
import com.finance.kafka.outbox.OutboxEventService;
import com.finance.paymentservice.client.CompanyServiceGateway.AccountDto;
import com.finance.paymentservice.client.CompanyServiceGateway.CompanyDto;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

import static com.finance.internal.security.AccessPolicy.ADMIN;
import static com.finance.internal.security.AccessPolicy.MANAGER;

@Service
@RequiredArgsConstructor
public class InternalCompanyService {
    private static final Set<String> ACCOUNT_TYPES = Set.of("CASH", "BANK", "CARD", "ELECTRONIC");
    private static final Set<String> TX_TYPES = Set.of("INCOME", "EXPENSE");
    private static final int MAX_PAGE = 500;

    private final CompanyRepository companies;
    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final UserRepository users;
    private final AccessPolicy policy;
    private final CompanyDirectoryService directory;
    private final OutboxEventService outbox;

    @Transactional(readOnly = true)
    public List<CompanyView> companies() {
        User u = policy.currentUser();
        List<Company> list = policy.canReadAll(u) ? companies.findAll()
                : policy.isManager(u) ? companies.findByOwnerId(u.getId()) : List.of();
        return list.stream().map(Views::company).toList();
    }

    @Transactional(readOnly = true)
    public CompanyView company(UUID id) {
        User u = policy.currentUser();
        return companies.findById(id).map(c -> {
            policy.requireReadCompany(u, c);
            return Views.company(directory.syncCompany(c));
        }).orElse(null);
    }

    @Transactional
    public MutationResult createCompany(CompanyInput in, UUID ownerId) {
        User u = policy.currentUser();
        policy.requireRole(u, ADMIN, MANAGER);
        User owner = u;
        if (ownerId != null && !ownerId.equals(u.getId())) {
            if (!policy.isAdmin(u)) throw new AccessDeniedException("Нельзя создавать компанию от имени другого пользователя");
            owner = users.findById(ownerId).orElseThrow(() -> new IllegalArgumentException("Владелец не найден"));
        }
        CompanyDto draft = new CompanyDto(null, in.name(), in.taxId(), in.address(), in.phone(), owner.getId(),
                in.description(), null, null, "ACTIVE");
        try {
            return Views.result(directory.createCompany(draft).getId(), "Компания создана");
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Компания с таким УНП уже существует");
        }
    }

    @Transactional
    public MutationResult updateCompany(UUID id, CompanyInput in) {
        User u = policy.currentUser();
        policy.requireRole(u, ADMIN, MANAGER);
        Company c = company(id, u, true);
        CompanyDto changes = new CompanyDto(c.getId(), in.name(), in.taxId(), in.address(), in.phone(),
                c.getOwner().getId(), in.description(), c.getWebsiteUrl(), c.getLogoUrl(), c.getStatus());
        try {
            directory.updateCompany(c, changes);
            companies.flush();
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Компания с таким УНП уже существует");
        }
        return Views.result(id, "Компания обновлена");
    }

    @Transactional
    public MutationResult deleteCompany(UUID id) {
        User u = policy.currentUser();
        policy.requireRole(u, ADMIN);
        Company c = company(id, u, true);
        try {
            directory.deleteCompany(c);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Нельзя удалить компанию: есть связанные счета, платежи или прогнозы");
        }
        return Views.result(id, "Компания удалена");
    }

    @Transactional(readOnly = true)
    public List<AccountView> accounts(UUID companyId) {
        User u = policy.currentUser();
        Company c = company(companyId, u, false);
        return accounts.findByCompanyId(c.getId()).stream().map(Views::account).toList();
    }

    @Transactional(readOnly = true)
    public AccountView account(UUID id) {
        User u = policy.currentUser();
        return accounts.findById(id).map(a -> {
            policy.requireReadCompany(u, a.getCompany());
            return Views.account(directory.syncAccount(a));
        }).orElse(null);
    }

    @Transactional
    public MutationResult createAccount(AccountInput in) {
        User u = policy.currentUser();
        policy.requireRole(u, ADMIN, MANAGER);
        Company c = company(in.companyId(), u, true);
        AccountDto draft = new AccountDto(null, c.getId(), in.accountName(), accountType(in.accountType()),
                currency(in.currency()), in.balance().setScale(2, RoundingMode.HALF_UP), true);
        return Views.result(directory.createAccount(draft).getId(), "Счёт создан");
    }

    @Transactional
    public MutationResult updateAccount(UUID id, AccountInput in) {
        User u = policy.currentUser();
        policy.requireRole(u, ADMIN, MANAGER);
        Account a = account(id, u, true);
        if (!a.getCompany().getId().equals(in.companyId())) {
            throw new IllegalArgumentException("Нельзя перенести счёт в другую компанию");
        }
        String newCurrency = currency(in.currency());
        BigDecimal newBalance = in.balance().setScale(2, RoundingMode.HALF_UP);
        boolean hasTransactions = transactions.existsByAccountId(id);
        if (hasTransactions) {
            if (newBalance.compareTo(a.getBalance()) != 0) {
                throw new IllegalArgumentException("Баланс счёта с операциями меняется только операциями: создайте приход или расход");
            }
            if (!newCurrency.equals(a.getCurrency())) {
                throw new IllegalArgumentException("Валюту счёта с операциями менять нельзя");
            }
        }
        directory.updateAccount(a, in.accountName(), accountType(in.accountType()),
                hasTransactions ? a.getCurrency() : newCurrency);
        if (!hasTransactions && newBalance.compareTo(a.getBalance()) != 0) {
            directory.applyBalance(a, newBalance.subtract(a.getBalance()), UUID.randomUUID());
        }
        return Views.result(id, "Счёт обновлён");
    }

    @Transactional
    public MutationResult deleteAccount(UUID id) {
        User u = policy.currentUser();
        policy.requireRole(u, ADMIN);
        Account a = account(id, u, true);
        if (transactions.existsByAccountId(id)) {
            throw new IllegalArgumentException("Нельзя удалить счёт, по которому есть операции");
        }
        try {
            directory.deleteAccount(a);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Нельзя удалить счёт: на него ссылаются платёжные заявки");
        }
        return Views.result(id, "Счёт удалён");
    }

    @Transactional(readOnly = true)
    public List<TransactionView> transactions(TransactionQuery q) {
        User u = policy.currentUser();
        Account acc = account(q.accountId(), u, false);
        List<Transaction> list = transactions.findByAccountIdOrderByDateDesc(acc.getId());
        final TransactionFilter f = q.filter();
        if (f != null) {
            list = list.stream()
                    .filter(t -> f.type() == null || f.type().equalsIgnoreCase(t.getTransactionType()))
                    .filter(t -> f.category() == null || f.category().equalsIgnoreCase(t.getCategory()))
                    .filter(t -> f.from() == null || !t.getDate().isBefore(f.from()))
                    .filter(t -> f.to() == null || !t.getDate().isAfter(f.to()))
                    .filter(t -> f.minAmount() == null || t.getAmount().compareTo(f.minAmount()) >= 0)
                    .filter(t -> f.maxAmount() == null || t.getAmount().compareTo(f.maxAmount()) <= 0)
                    .toList();
        }
        TransactionSort sort = q.sort();
        if (sort != null && sort.field() != null) {
            Comparator<Transaction> c = switch (sort.field()) {
                case "amount" -> Comparator.comparing(Transaction::getAmount);
                case "category" -> Comparator.comparing((Transaction t) -> Optional.ofNullable(t.getCategory()).orElse(""));
                default -> Comparator.comparing(Transaction::getDate);
            };
            if ("DESC".equalsIgnoreCase(sort.direction())) c = c.reversed();
            list = list.stream().sorted(c).toList();
        }
        int from = Math.max(0, q.offset() == null ? 0 : q.offset());
        int size = Math.min(MAX_PAGE, Math.max(1, q.limit() == null ? 100 : q.limit()));
        int to = Math.min(list.size(), from + size);
        return from >= list.size() ? List.of() : list.subList(from, to).stream().map(Views::transaction).toList();
    }

    @Transactional(readOnly = true)
    public TransactionView transaction(UUID id) {
        User u = policy.currentUser();
        return transactions.findById(id).map(t -> {
            policy.requireReadCompany(u, t.getAccount().getCompany());
            return Views.transaction(t);
        }).orElse(null);
    }

    @Transactional(readOnly = true)
    public AggregateView aggregate(UUID companyId, LocalDate from, LocalDate to) {
        User u = policy.currentUser();
        Company c = company(companyId, u, false);
        List<Transaction> tx = transactions.findByAccountCompanyIdAndDateBetween(c.getId(),
                from == null ? LocalDate.of(2000, 1, 1) : from, to == null ? LocalDate.now() : to);
        BigDecimal income = sum(tx, "INCOME");
        BigDecimal expense = sum(tx, "EXPENSE");
        BigDecimal total = tx.stream().map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = tx.isEmpty() ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(tx.size()), 2, RoundingMode.HALF_UP);
        return new AggregateView(income, expense, income.subtract(expense), tx.size(), avg);
    }

    @Transactional
    public MutationResult createTransaction(TransactionInput in) {
        User u = policy.currentUser();
        policy.requireRole(u, ADMIN, MANAGER);
        Account a = account(in.accountId(), u, true);
        String type = txType(in.transactionType());
        Transaction t = new Transaction();
        t.setAccount(a);
        apply(t, in, type);
        Transaction saved = transactions.saveAndFlush(t);
        directory.applyBalance(a, signed(type, t.getAmount()), saved.getId());
        outbox.enqueue(EventTypes.TRANSACTION_CREATED, saved.getId(), a.getCompany().getId(),
                "transactionId", saved.getId(), "accountId", a.getId(), "type", type,
                "amount", t.getAmount().toPlainString(), "date", t.getDate(),
                "balanceAfter", a.getBalance().toPlainString());
        return Views.result(saved.getId(), "Транзакция создана");
    }

    @Transactional
    public MutationResult updateTransaction(UUID id, TransactionInput in) {
        User u = policy.currentUser();
        policy.requireRole(u, ADMIN, MANAGER);
        Transaction t = transactions.findById(id).orElseThrow(() -> new NoSuchElementException("Транзакция не найдена"));
        Account oldAccount = t.getAccount();
        policy.requireWriteCompany(u, oldAccount.getCompany());
        Account newAccount = account(in.accountId(), u, true);
        if (!oldAccount.getCurrency().equalsIgnoreCase(newAccount.getCurrency())) {
            throw new IllegalArgumentException("Нельзя перенести операцию на счёт с другой валютой");
        }

        UUID previousAccountId = oldAccount.getId();
        UUID previousCompanyId = oldAccount.getCompany().getId();
        String type = txType(in.transactionType());

        directory.applyBalance(oldAccount, signed(t.getTransactionType(), t.getAmount()).negate(), UUID.randomUUID());
        t.setAccount(newAccount);
        apply(t, in, type);
        directory.applyBalance(newAccount, signed(type, t.getAmount()), UUID.randomUUID());
        transactions.save(t);

        UUID companyId = newAccount.getCompany().getId();
        outbox.enqueue(EventTypes.TRANSACTION_UPDATED, id, companyId,
                "transactionId", id, "accountId", newAccount.getId(),
                "previousAccountId", previousAccountId,
                "previousCompanyId", previousCompanyId.equals(companyId) ? null : previousCompanyId,
                "type", type, "amount", t.getAmount().toPlainString(), "date", t.getDate(),
                "balanceAfter", newAccount.getBalance().toPlainString());

        return Views.result(id, "Транзакция обновлена");
    }

    @Transactional
    public MutationResult deleteTransaction(UUID id) {
        User u = policy.currentUser();
        policy.requireRole(u, ADMIN);
        Transaction t = transactions.findById(id).orElseThrow(() -> new NoSuchElementException("Транзакция не найдена"));
        Account a = t.getAccount();
        policy.requireWriteCompany(u, a.getCompany());

        String type = t.getTransactionType();
        BigDecimal amount = t.getAmount();
        var date = t.getDate();

        directory.applyBalance(a, signed(type, amount).negate(), UUID.randomUUID());
        transactions.delete(t);

        outbox.enqueue(EventTypes.TRANSACTION_DELETED, id, a.getCompany().getId(),
                "transactionId", id, "accountId", a.getId(), "type", type,
                "amount", amount.toPlainString(), "date", date,
                "balanceAfter", a.getBalance().toPlainString());

        return Views.result(id, "Транзакция удалена");
    }

    private BigDecimal signed(String type, BigDecimal amount) {
        return "INCOME".equalsIgnoreCase(type) ? amount : amount.negate();
    }

    private void apply(Transaction t, TransactionInput in, String type) {
        t.setTransactionType(type);
        t.setAmount(in.amount().setScale(2, RoundingMode.HALF_UP));
        t.setDate(in.date());
        t.setDescription(in.description());
        t.setCategory(in.category());
        t.setCounterparty(in.counterparty());
        t.setCfoId(in.cfoId());
    }

    private BigDecimal sum(List<Transaction> tx, String type) {
        return tx.stream().filter(t -> type.equalsIgnoreCase(t.getTransactionType()))
                .map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Company company(UUID id, User u, boolean write) {
        Company c = companies.findById(id).orElseThrow(() -> new NoSuchElementException("Компания не найдена"));
        if (write) policy.requireWriteCompany(u, c); else policy.requireReadCompany(u, c);
        return c;
    }

    private Account account(UUID id, User u, boolean write) {
        Account a = accounts.findById(id).orElseThrow(() -> new NoSuchElementException("Счёт не найден"));
        if (write) policy.requireWriteCompany(u, a.getCompany()); else policy.requireReadCompany(u, a.getCompany());
        return a;
    }

    private String accountType(String raw) {
        String t = raw.trim().toUpperCase(Locale.ROOT);
        if (!ACCOUNT_TYPES.contains(t)) throw new IllegalArgumentException("Тип счёта: CASH, BANK, CARD или ELECTRONIC");
        return t;
    }

    private String currency(String raw) {
        String c = raw.trim().toUpperCase(Locale.ROOT);
        if (!c.matches("[A-Z]{3}")) throw new IllegalArgumentException("Валюта — трёхбуквенный код, например BYN");
        return c;
    }

    private String txType(String raw) {
        String t = raw.trim().toUpperCase(Locale.ROOT);
        if (!TX_TYPES.contains(t)) throw new IllegalArgumentException("transactionType должен быть INCOME или EXPENSE");
        return t;
    }
}
