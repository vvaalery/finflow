package com.finance.internal.service;

import com.finance.backend.entity.Account;
import com.finance.backend.entity.Company;
import com.finance.backend.entity.Transaction;
import com.finance.backend.entity.User;
import com.finance.backend.repository.AccountRepository;
import com.finance.backend.repository.CompanyRepository;
import com.finance.backend.repository.TransactionRepository;
import com.finance.backend.repository.UserRepository;
import com.finance.internal.dto.GraphQLInput.*;
import com.finance.internal.dto.GraphQLView.*;
import com.finance.internal.security.AccessPolicy;
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
            return Views.company(c);
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
        Company c = new Company();
        fill(c, in);
        c.setOwner(owner);
        c.setStatus("ACTIVE");
        try {
            return Views.result(companies.saveAndFlush(c).getId(), "Компания создана");
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Компания с таким УНП уже существует");
        }
    }

    @Transactional
    public MutationResult updateCompany(UUID id, CompanyInput in) {
        User u = policy.currentUser();
        policy.requireRole(u, ADMIN, MANAGER);
        Company c = company(id, u, true);
        fill(c, in);
        try {
            companies.saveAndFlush(c);
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
            companies.delete(c);
            companies.flush();
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Нельзя удалить компанию: есть связанные счета, платежи или прогнозы");
        }
        return Views.result(id, "Компания удалена");
    }

    private void fill(Company c, CompanyInput in) {
        c.setName(in.name());
        c.setTaxId(in.taxId());
        c.setAddress(in.address());
        c.setPhone(in.phone());
        c.setDescription(in.description());
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
            return Views.account(a);
        }).orElse(null);
    }

    @Transactional
    public MutationResult createAccount(AccountInput in) {
        User u = policy.currentUser();
        policy.requireRole(u, ADMIN, MANAGER);
        Company c = company(in.companyId(), u, true);
        Account a = new Account();
        a.setCompany(c);
        a.setAccountName(in.accountName());
        a.setAccountType(accountType(in.accountType()));
        a.setCurrency(currency(in.currency()));
        a.setBalance(in.balance().setScale(2, RoundingMode.HALF_UP));
        a.setIsActive(true);
        return Views.result(accounts.save(a).getId(), "Счёт создан");
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
        if (transactions.existsByAccountId(id)) {
            if (newBalance.compareTo(a.getBalance()) != 0) {
                throw new IllegalArgumentException("Баланс счёта с операциями меняется только операциями: создайте приход или расход");
            }
            if (!newCurrency.equals(a.getCurrency())) {
                throw new IllegalArgumentException("Валюту счёта с операциями менять нельзя");
            }
        } else {
            a.setBalance(newBalance);
            a.setCurrency(newCurrency);
        }
        a.setAccountName(in.accountName());
        a.setAccountType(accountType(in.accountType()));
        accounts.save(a);
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
            accounts.delete(a);
            accounts.flush();
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
        post(a, type, t.getAmount());
        accounts.save(a);
        return Views.result(transactions.save(t).getId(), "Транзакция создана");
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
        String type = txType(in.transactionType());
        unpost(oldAccount, t.getTransactionType(), t.getAmount());
        t.setAccount(newAccount);
        apply(t, in, type);
        post(newAccount, type, t.getAmount());
        accounts.save(oldAccount);
        accounts.save(newAccount);
        transactions.save(t);
        return Views.result(id, "Транзакция обновлена");
    }

    @Transactional
    public MutationResult deleteTransaction(UUID id) {
        User u = policy.currentUser();
        policy.requireRole(u, ADMIN);
        Transaction t = transactions.findById(id).orElseThrow(() -> new NoSuchElementException("Транзакция не найдена"));
        Account a = t.getAccount();
        policy.requireWriteCompany(u, a.getCompany());
        unpost(a, t.getTransactionType(), t.getAmount());
        accounts.save(a);
        transactions.delete(t);
        return Views.result(id, "Транзакция удалена");
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

    private void post(Account a, String type, BigDecimal amount) {
        a.setBalance("INCOME".equals(type) ? a.getBalance().add(amount) : a.getBalance().subtract(amount));
    }

    private void unpost(Account a, String type, BigDecimal amount) {
        a.setBalance("INCOME".equalsIgnoreCase(type) ? a.getBalance().subtract(amount) : a.getBalance().add(amount));
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
