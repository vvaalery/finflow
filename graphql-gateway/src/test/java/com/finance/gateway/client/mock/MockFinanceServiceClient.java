package com.finance.gateway.client.mock;

import com.finance.gateway.client.CompanyServiceClient;
import com.finance.gateway.client.ForecastServiceClient;
import com.finance.gateway.client.PaymentServiceClient;
import com.finance.gateway.security.AuthenticatedUser;
import com.finance.graphqlapi.dto.GraphQLInput.*;
import com.finance.graphqlapi.dto.GraphQLView.*;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MockFinanceServiceClient implements CompanyServiceClient, PaymentServiceClient, ForecastServiceClient {
    private final Map<UUID, CompanyView> companies = new ConcurrentHashMap<>();
    private final Map<UUID, AccountView> accounts = new ConcurrentHashMap<>();
    private final Map<UUID, TransactionView> transactions = new ConcurrentHashMap<>();
    private final Map<UUID, PaymentView> payments = new ConcurrentHashMap<>();
    private final Map<UUID, PaymentRequestView> requests = new ConcurrentHashMap<>();
    private final Map<UUID, ForecastView> forecasts = new ConcurrentHashMap<>();

    public MockFinanceServiceClient() {
        UUID owner = MockAuthServiceClient.idFor("admin@finflow.local");
        UUID cid = UUID.randomUUID();
        companies.put(cid, new CompanyView(cid, "Mock Company", "100000001", "Minsk", "+375", "Тестовая компания", null, null, "ACTIVE", owner, LocalDateTime.now()));
        UUID aid = UUID.randomUUID();
        accounts.put(aid, new AccountView(aid, cid, "Основной счёт", "BANK", "BYN", new BigDecimal("10000.00"), true, LocalDateTime.now()));
    }

    private UserView me() {
        return AuthenticatedUser.current().user();
    }

    private boolean readsAll() {
        return Set.of("ADMIN", "DIRECTOR").contains(me().role().name());
    }

    private boolean visible(CompanyView c) {
        return readsAll() || c.ownerId().equals(me().id());
    }

    private CompanyView visibleCompany(UUID id) {
        CompanyView c = companies.get(id);
        if (c == null) {
            throw new NoSuchElementException("Компания не найдена");
        }
        if (!visible(c)) {
            throw new AccessDeniedException("Нет доступа к данным этой компании");
        }
        return c;
    }

    private MutationResult ok(UUID id, String msg) {
        return new MutationResult(true, msg, id.toString());
    }

    @Override
    public List<CompanyView> companies() {
        return companies.values().stream().filter(this::visible).toList();
    }

    @Override
    public CompanyView company(UUID id) {
        return companies.containsKey(id) ? visibleCompany(id) : null;
    }

    @Override
    public MutationResult createCompany(CompanyInput input, UUID ownerId) {
        UUID owner = ownerId != null && "ADMIN".equals(me().role().name()) ? ownerId : me().id();
        UUID id = UUID.randomUUID();
        companies.put(id, new CompanyView(id, input.name(), input.taxId(), input.address(), input.phone(), input.description(), null, null, "ACTIVE", owner, LocalDateTime.now()));
        return ok(id, "Компания создана");
    }

    @Override
    public MutationResult updateCompany(UUID id, CompanyInput input) {
        CompanyView c = visibleCompany(id);
        companies.put(id, new CompanyView(id, input.name(), input.taxId(), input.address(), input.phone(), input.description(), c.websiteUrl(), c.logoUrl(), c.status(), c.ownerId(), c.createdAt()));
        return ok(id, "Компания обновлена");
    }

    @Override
    public MutationResult deleteCompany(UUID id) {
        visibleCompany(id);
        companies.remove(id);
        return ok(id, "Компания удалена");
    }

    @Override
    public List<AccountView> accounts(UUID companyId) {
        visibleCompany(companyId);
        return accounts.values().stream().filter(a -> a.companyId().equals(companyId)).toList();
    }

    @Override
    public AccountView account(UUID id) {
        AccountView a = accounts.get(id);
        if (a != null) {
            visibleCompany(a.companyId());
        }
        return a;
    }

    @Override
    public MutationResult createAccount(AccountInput input) {
        visibleCompany(input.companyId());
        UUID id = UUID.randomUUID();
        accounts.put(id, new AccountView(id, input.companyId(), input.accountName(), input.accountType().toUpperCase(), input.currency().toUpperCase(), input.balance(), true, LocalDateTime.now()));
        return ok(id, "Счёт создан");
    }

    @Override
    public MutationResult updateAccount(UUID id, AccountInput input) {
        AccountView a = account(id);
        if (a == null) {
            throw new NoSuchElementException("Счёт не найден");
        }
        boolean hasTx = transactions.values().stream().anyMatch(t -> t.accountId().equals(id));
        if (hasTx && input.balance().compareTo(a.balance()) != 0) {
            throw new IllegalArgumentException("Баланс счёта с операциями меняется только операциями");
        }
        accounts.put(id, new AccountView(id, a.companyId(), input.accountName(), input.accountType().toUpperCase(), hasTx ? a.currency() : input.currency().toUpperCase(), hasTx ? a.balance() : input.balance(), true, a.createdAt()));
        return ok(id, "Счёт обновлён");
    }

    @Override
    public MutationResult deleteAccount(UUID id) {
        if (account(id) == null) {
            throw new NoSuchElementException("Счёт не найден");
        }
        accounts.remove(id);
        return ok(id, "Счёт удалён");
    }

    @Override
    public List<TransactionView> transactions(TransactionQuery q) {
        if (account(q.accountId()) == null) {
            throw new NoSuchElementException("Счёт не найден");
        }
        var list = transactions.values().stream()
                .filter(t -> t.accountId().equals(q.accountId()))
                .filter(t -> q.filter() == null || q.filter().type() == null || q.filter().type().equalsIgnoreCase(t.transactionType()))
                .sorted(Comparator.comparing(TransactionView::date).reversed())
                .toList();
        int from = Math.max(0, q.offset() == null ? 0 : q.offset());
        int to = Math.min(list.size(), from + (q.limit() == null ? 100 : q.limit()));
        return from >= list.size() ? List.of() : list.subList(from, to);
    }

    @Override
    public TransactionView transaction(UUID id) {
        return transactions.get(id);
    }

    @Override
    public MutationResult createTransaction(TransactionInput input) {
        AccountView a = account(input.accountId());
        if (a == null) {
            throw new NoSuchElementException("Счёт не найден");
        }
        String type = input.transactionType().toUpperCase();
        if (!type.equals("INCOME") && !type.equals("EXPENSE")) {
            throw new IllegalArgumentException("transactionType должен быть INCOME или EXPENSE");
        }
        BigDecimal balance = type.equals("INCOME") ? a.balance().add(input.amount()) : a.balance().subtract(input.amount());
        accounts.put(a.id(), new AccountView(a.id(), a.companyId(), a.accountName(), a.accountType(), a.currency(), balance, true, a.createdAt()));
        UUID id = UUID.randomUUID();
        transactions.put(id, new TransactionView(id, a.id(), type, input.amount(), input.date(), input.description(), input.category(), input.counterparty(), input.cfoId(), LocalDateTime.now()));
        return ok(id, "Транзакция создана");
    }

    @Override
    public MutationResult updateTransaction(UUID id, TransactionInput input) {
        throw new UnsupportedOperationException("Не поддерживается в mock-режиме");
    }

    @Override
    public MutationResult deleteTransaction(UUID id) {
        transactions.remove(id);
        return ok(id, "Транзакция удалена");
    }

    @Override
    public AggregateView aggregate(UUID companyId, LocalDate from, LocalDate to) {
        visibleCompany(companyId);
        var ids = accounts.values().stream().filter(a -> a.companyId().equals(companyId)).map(AccountView::id).toList();
        var tx = transactions.values().stream().filter(t -> ids.contains(t.accountId())).toList();
        BigDecimal totalIn = tx.stream().filter(t -> t.transactionType().equals("INCOME")).map(TransactionView::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalOut = tx.stream().filter(t -> t.transactionType().equals("EXPENSE")).map(TransactionView::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = tx.isEmpty() ? BigDecimal.ZERO : totalIn.add(totalOut).divide(BigDecimal.valueOf(tx.size()), 2, java.math.RoundingMode.HALF_UP);
        return new AggregateView(totalIn, totalOut, totalIn.subtract(totalOut), tx.size(), avg);
    }

    @Override
    public List<PaymentView> payments(UUID companyId) {
        visibleCompany(companyId);
        return payments.values().stream().filter(p -> p.companyId().equals(companyId)).toList();
    }

    @Override
    public MutationResult createPayment(PaymentInput input) {
        visibleCompany(input.companyId());
        if (input.status() != null && !input.status().isBlank() && !"PENDING".equalsIgnoreCase(input.status())) {
            throw new IllegalArgumentException("Платёж создаётся в статусе PENDING");
        }
        return addPayment(input.companyId(), input.title(), input.description(), input.amount(), input.dueDate(), "PENDING", input.category(), input.counterparty(), "Платёж добавлен");
    }

    @Override
    public MutationResult updatePayment(UUID id, PaymentInput input) {
        PaymentView p = payments.get(id);
        if (p == null) {
            throw new NoSuchElementException("Платёж не найден");
        }
        visibleCompany(p.companyId());
        payments.put(id, new PaymentView(id, p.companyId(), input.title(), input.description(), input.amount(), input.dueDate(), p.status(), input.category(), input.counterparty(), p.createdBy(), p.createdAt()));
        return ok(id, "Платёж обновлён");
    }

    @Override
    public MutationResult deletePayment(UUID id) {
        if (payments.remove(id) == null) {
            throw new NoSuchElementException("Платёж не найден");
        }
        return ok(id, "Платёж удалён");
    }

    private MutationResult addPayment(UUID companyId, String title, String desc, BigDecimal amount, LocalDate due, String status, String cat, String cp, String msg) {
        UUID id = UUID.randomUUID();
        payments.put(id, new PaymentView(id, companyId, title, desc, amount, due, status, cat, cp, me().id(), LocalDateTime.now()));
        return ok(id, msg);
    }

    @Override
    public List<PaymentRequestView> paymentRequests() {
        return requests.values().stream().filter(r -> companies.containsKey(r.companyId()) && visible(companies.get(r.companyId()))).toList();
    }

    @Override
    public PaymentRequestView paymentRequest(UUID id) {
        return requests.get(id);
    }

    @Override
    public MutationResult createPaymentRequest(PaymentRequestInput input) {
        visibleCompany(input.companyId());
        AccountView a = accounts.get(input.accountId());
        if (a == null || !a.companyId().equals(input.companyId())) {
            throw new IllegalArgumentException("Счёт не существует или не принадлежит указанной компании");
        }
        UUID id = UUID.randomUUID();
        requests.put(id, new PaymentRequestView(id, input.accountId(), input.companyId(), input.title(), input.amount(), input.date(), "PENDING", me().fullName(), input.counterparty(), input.category(), input.description()));
        return ok(id, "Платёжная заявка создана");
    }

    @Override
    public MutationResult approvePaymentRequest(UUID id) {
        PaymentRequestView r = pending(id);
        requests.put(id, withStatus(r, "APPROVED"));
        addPayment(r.companyId(), r.title(), r.description(), r.amount(), r.date(), "APPROVED", r.category(), r.counterparty(), "");
        return ok(id, "Заявка утверждена, платёж добавлен в календарь");
    }

    @Override
    public MutationResult rejectPaymentRequest(UUID id) {
        requests.put(id, withStatus(pending(id), "REJECTED"));
        return ok(id, "Заявка отклонена");
    }

    private PaymentRequestView pending(UUID id) {
        PaymentRequestView r = requests.get(id);
        if (r == null) {
            throw new NoSuchElementException("Заявка не найдена");
        }
        if (!"PENDING".equals(r.status())) {
            throw new IllegalArgumentException("Недопустимый переход статуса заявки: " + r.status());
        }
        return r;
    }

    private PaymentRequestView withStatus(PaymentRequestView r, String status) {
        return new PaymentRequestView(r.id(), r.accountId(), r.companyId(), r.title(), r.amount(), r.date(), status, r.requester(), r.counterparty(), r.category(), r.description());
    }

    @Override
    public List<ForecastView> forecasts(UUID companyId) {
        visibleCompany(companyId);
        return forecasts.values().stream().filter(f -> f.companyId().equals(companyId)).toList();
    }

    @Override
    public ForecastResultView generateForecast(GenerateForecastInput input) {
        visibleCompany(input.companyId());
        int months = input.months() == null ? 6 : input.months();
        List<ForecastPointView> items = new ArrayList<>();
        BigDecimal balance = new BigDecimal("10000.00");
        LocalDate start = LocalDate.now().withDayOfMonth(1).plusMonths(1);

        for (int i = 0; i < months; i++) {
            BigDecimal inflow = new BigDecimal(50000 + i * 500);
            BigDecimal outflow = new BigDecimal(30000 + i * 200);
            BigDecimal net = inflow.subtract(outflow);
            balance = balance.add(net);
            items.add(new ForecastPointView(start.plusMonths(i), inflow, outflow, net, inflow, inflow, outflow, outflow, net, net, balance));
        }

        BigDecimal totalIn = items.stream().map(ForecastPointView::inflow).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalOut = items.stream().map(ForecastPointView::outflow).reduce(BigDecimal.ZERO, BigDecimal::add);
        UUID id = UUID.randomUUID();
        String method = input.method() == null ? "GRADIENT_BOOSTING" : input.method();
        String scenario = input.scenario() == null ? "BASE" : input.scenario();

        forecasts.put(id, new ForecastView(id, input.companyId(), start, start.plusMonths(months).minusDays(1), totalIn, totalOut, totalIn.subtract(totalOut), method, LocalDateTime.now()));

        return new ForecastResultView(id, input.companyId(), method, scenario, start, start.plusMonths(months).minusDays(1), totalIn, totalOut, totalIn.subtract(totalOut), new BigDecimal("10000.00"), balance, items, List.of("Mock: данные демонстрационные"));
    }
}
