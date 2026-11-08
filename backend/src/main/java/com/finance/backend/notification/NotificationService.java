package com.finance.backend.notification;

import com.finance.backend.analytics.*;
import com.finance.backend.entity.Company;
import com.finance.backend.entity.PaymentCalendar;
import com.finance.backend.entity.PaymentRequest;
import com.finance.backend.entity.User;
import com.finance.backend.repository.CompanyRepository;
import com.finance.backend.repository.PaymentCalendarRepository;
import com.finance.backend.repository.PaymentRequestRepository;
import com.finance.backend.repository.UserRepository;
import com.finance.backend.service.CashFlowHistoryService;
import com.finance.backend.service.ForecastService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private static final long SYNC_INTERVAL_MS = 20_000;
    private static final Locale RU = Locale.forLanguageTag("ru");

    private final NotificationRepository repository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final PaymentCalendarRepository paymentRepository;
    private final PaymentRequestRepository requestRepository;
    private final CashFlowHistoryService historyService;
    private final PlatformTransactionManager transactionManager;

    private final Map<UUID, Long> lastSync = new ConcurrentHashMap<>();
    private final Map<UUID, Object> locks = new ConcurrentHashMap<>();

    private record Candidate(String key, String type, String severity, String title, String message, String linkTab) {}

    public User userByEmail(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new NoSuchElementException("Пользователь не найден"));
    }

    public void syncIfNeeded(User user, boolean force) {
        long now = System.currentTimeMillis();
        Long last = lastSync.get(user.getId());
        if (!force && last != null && now - last < SYNC_INTERVAL_MS) return;
        synchronized (locks.computeIfAbsent(user.getId(), k -> new Object())) {
            List<Candidate> candidates = buildCandidates(user); // чтение: вне транзакции записи
            new TransactionTemplate(transactionManager).executeWithoutResult(s -> apply(user, candidates));
            lastSync.put(user.getId(), System.currentTimeMillis());
        }
    }

    @Transactional(readOnly = true)
    public NotificationView.Page page(UUID userId, boolean unreadOnly) {
        List<NotificationView.Item> items = repository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(n -> !n.isDismissed())
                .filter(n -> !unreadOnly || !n.isSeen())
                .sorted(Comparator.comparingInt((Notification n) -> rank(n.getSeverity()))
                        .thenComparing(Notification::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(NotificationView.Item::of).toList();
        return new NotificationView.Page(repository.countByUserIdAndSeenFalseAndDismissedFalse(userId), items);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) { return repository.countByUserIdAndSeenFalseAndDismissedFalse(userId); }

    @Transactional
    public void markRead(UUID userId, UUID id, boolean read) {
        Notification n = repository.findByIdAndUserId(id, userId).orElseThrow(() -> new NoSuchElementException("Уведомление не найдено"));
        n.setSeen(read); n.setReadAt(read ? LocalDateTime.now() : null);
        repository.save(n);
    }

    @Transactional
    public int markAllRead(UUID userId) { return repository.markAllRead(userId, LocalDateTime.now()); }

    @Transactional
    public void delete(UUID userId, UUID id) {
        Notification n = repository.findByIdAndUserId(id, userId).orElseThrow(() -> new NoSuchElementException("Уведомление не найдено"));
        n.setDismissed(true); n.setSeen(true);
        repository.save(n);
    }

    private static int rank(String severity) {
        return switch (severity) { case "CRITICAL" -> 0; case "WARNING" -> 1; case "INFO" -> 2; default -> 3; };
    }

    private List<Candidate> buildCandidates(User user) {
        boolean director = user.getRole() != null && "DIRECTOR".equals(user.getRole().getName());
        List<Company> companies = director ? companyRepository.findAll() : companyRepository.findByOwnerId(user.getId());
        List<Candidate> candidates = new ArrayList<>();
        for (Company c : companies) collect(c, companies.size() > 1, candidates);
        return candidates;
    }

    private void apply(User user, List<Candidate> candidates) {
        Map<String, Notification> existing = repository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .collect(Collectors.toMap(Notification::getDedupeKey, n -> n, (a, b) -> a));
        Set<String> active = new HashSet<>();
        for (Candidate c : candidates) {
            if (!active.add(c.key())) continue;
            Notification n = existing.get(c.key());
            if (n == null) { n = new Notification(); n.setUserId(user.getId()); n.setDedupeKey(c.key()); }
            n.setType(c.type()); n.setSeverity(c.severity()); n.setTitle(c.title()); n.setMessage(c.message()); n.setLinkTab(c.linkTab());
            repository.save(n);
        }
        existing.values().stream().filter(n -> !active.contains(n.getDedupeKey())).forEach(repository::delete);
    }

    private void collect(Company company, boolean multi, List<Candidate> out) {
        UUID cid = company.getId();
        String who = multi ? company.getName() + ": " : "";
        LocalDate today = LocalDate.now();

        List<PaymentCalendar> unpaid = paymentRepository.findByCompanyId(cid).stream()
                .filter(p -> !"PAID".equals(p.getStatus())).toList();
        List<PaymentCalendar> overdue = unpaid.stream().filter(p -> p.getDueDate().isBefore(today))
                .sorted(Comparator.comparing(PaymentCalendar::getDueDate)).toList();
        if (!overdue.isEmpty()) {
            out.add(new Candidate("overdue:" + cid + ":" + fingerprint(overdue.stream().map(PaymentCalendar::getId).toList()),
                    "PAYMENT_OVERDUE", "CRITICAL", who + "Просроченные платежи: " + overdue.size(),
                    "На сумму " + fmt(sumPayments(overdue)) + " BYN. Самый старый срок — " + overdue.get(0).getDueDate() + " (" + overdue.get(0).getTitle() + ").",
                    "payments"));
        }
        List<PaymentCalendar> upcoming = unpaid.stream()
                .filter(p -> !p.getDueDate().isBefore(today) && !p.getDueDate().isAfter(today.plusDays(7)))
                .sorted(Comparator.comparing(PaymentCalendar::getDueDate)).toList();
        if (!upcoming.isEmpty()) {
            boolean soon = !upcoming.get(0).getDueDate().isAfter(today.plusDays(2));
            out.add(new Candidate("upcoming:" + cid + ":" + fingerprint(upcoming.stream().map(PaymentCalendar::getId).toList()),
                    "PAYMENT_UPCOMING", soon ? "WARNING" : "INFO", who + "Платежи в ближайшие 7 дней: " + upcoming.size(),
                    "К оплате " + fmt(sumPayments(upcoming)) + " BYN. Ближайший срок — " + upcoming.get(0).getDueDate() + " (" + upcoming.get(0).getTitle() + ").",
                    "payments"));
        }

        List<PaymentRequest> pending = requestRepository.findAll().stream()
                .filter(r -> "PENDING".equals(r.getStatus()) && r.getAccount() != null && r.getAccount().getCompany() != null
                        && cid.equals(r.getAccount().getCompany().getId())).toList();
        if (!pending.isEmpty()) {
            BigDecimal total = pending.stream().map(r -> r.getAmount() == null ? BigDecimal.ZERO : r.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
            out.add(new Candidate("requests:" + cid + ":" + fingerprint(pending.stream().map(PaymentRequest::getId).toList()),
                    "REQUEST_PENDING", "INFO", who + "Заявки ожидают решения: " + pending.size(),
                    "Общая сумма заявок — " + fmt(total) + " BYN.", "requests"));
        }

        CashFlowHistoryService.History h = historyService.load(cid);
        if (h.excludedOtherCurrency() > 0) {
            out.add(new Candidate("data:fx:" + cid, "DATA_QUALITY", "INFO", who + "Операции в иностранной валюте исключены из аналитики",
                    "Операций: " + h.excludedOtherCurrency() + ". Прогноз строится только по BYN, пока не заданы курсы валют.", "transactions"));
        }
        if (h.months() < ForecastService.MIN_HISTORY_MONTHS) {
            out.add(new Candidate("data:short:" + cid, "DATA_QUALITY", "INFO", who + "Недостаточно данных для прогноза",
                    "Нужно минимум " + ForecastService.MIN_HISTORY_MONTHS + " завершённых месяцев операций, сейчас — " + h.months() + ". Импортируйте историю операций.", "import"));
            return;
        }
        forecastRisk(company, who, h, out);
        anomalies(cid, who, h, out);
    }

    private void forecastRisk(Company company, String who, CashFlowHistoryService.History h, List<Candidate> out) {
        UUID cid = company.getId();
        ForecastEngine.Result r = ForecastEngine.forecast(ForecastMethod.GRADIENT_BOOSTING, Scenario.BASE, h.inflow(), h.outflow(), h.start(), 3);
        double balance = historyService.baseCurrencyBalance(cid).doubleValue(), running = balance;
        YearMonth deficit = null, negativeNet = null;
        for (ForecastEngine.Point p : r.points()) {
            running += p.net();
            if (running < 0 && deficit == null) deficit = p.month();
            if (p.net() < 0 && negativeNet == null) negativeNet = p.month();
        }
        String cycle = YearMonth.now().toString();
        if (deficit != null) {
            out.add(new Candidate("forecast:deficit:" + cid + ":" + cycle, "FORECAST_RISK", "CRITICAL", who + "Риск нехватки ликвидности",
                    "По прогнозу градиентного бустинга остаток на счетах в BYN станет отрицательным в " + monthName(deficit)
                            + ". Остаток сейчас: " + fmt(BigDecimal.valueOf(balance)) + " BYN.", "forecast"));
        } else if (negativeNet != null) {
            out.add(new Candidate("forecast:negative:" + cid + ":" + cycle, "FORECAST_RISK", "WARNING", who + "Прогноз: отрицательный денежный поток",
                    "Ожидается превышение расходов над поступлениями в " + monthName(negativeNet) + ". Накопленный остаток пока положительный.", "forecast"));
        }
    }

    private void anomalies(UUID cid, String who, CashFlowHistoryService.History h, List<Candidate> out) {
        int last = h.months() - 1;
        SeriesStats exp = SeriesAnalyzer.analyze(h.outflow()), inc = SeriesAnalyzer.analyze(h.inflow());
        for (SeriesStats.Anomaly a : exp.anomalies()) {
            if (a.index() < last - 1 || a.score() <= 0) continue;
            YearMonth m = h.start().plusMonths(a.index());
            out.add(new Candidate("anomaly:out:" + cid + ":" + m, "EXPENSE_ANOMALY", "WARNING", who + "Аномальный рост расходов — " + monthName(m),
                    "Расходы " + fmt(BigDecimal.valueOf(a.value())) + " BYN при ожидаемых около " + fmt(BigDecimal.valueOf(a.expected())) + " BYN.", "reports"));
        }
        for (SeriesStats.Anomaly a : inc.anomalies()) {
            if (a.index() < last - 1 || a.score() >= 0) continue;
            YearMonth m = h.start().plusMonths(a.index());
            out.add(new Candidate("anomaly:in:" + cid + ":" + m, "INCOME_ANOMALY", "WARNING", who + "Необычное падение поступлений — " + monthName(m),
                    "Поступления " + fmt(BigDecimal.valueOf(a.value())) + " BYN при ожидаемых около " + fmt(BigDecimal.valueOf(a.expected())) + " BYN.", "reports"));
        }
    }

    private static BigDecimal sumPayments(List<PaymentCalendar> list) {
        return list.stream().map(PaymentCalendar::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String fingerprint(List<UUID> ids) {
        return Integer.toHexString(ids.stream().map(UUID::toString).sorted().collect(Collectors.joining(",")).hashCode());
    }

    private static String fmt(BigDecimal v) { return String.format(RU, "%,.0f", v.setScale(0, java.math.RoundingMode.HALF_UP)); }

    private static String monthName(YearMonth m) {
        return m.getMonth().getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, RU) + " " + m.getYear();
    }
}
