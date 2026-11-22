package com.finance.backend.controller;

import com.finance.backend.entity.User;
import com.finance.backend.repository.CompanyRepository;
import com.finance.backend.repository.PaymentCalendarRepository;
import com.finance.backend.repository.TransactionRepository;
import com.finance.backend.repository.UserRepository;
import com.finance.backend.service.AnalyticsInsightsService;
import com.finance.internal.security.RestGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@PreAuthorize("hasAnyRole('ADMIN','FINANCIAL_MANAGER','DIRECTOR')")
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final TransactionRepository transactionRepository;
    private final PaymentCalendarRepository paymentRepository;
    private final AnalyticsInsightsService insightsService;
    private final RestGuard guard;

    private boolean all(User u) {
        return guard.policy().canReadAll(u);
    }

    private BigDecimal income(User u, LocalDate s, LocalDate e) {
        BigDecimal v = all(u)
                ? transactionRepository.sumIncome(s, e)
                : transactionRepository.sumIncomeByOwner(u.getId(), s, e);
        return v != null ? v : BigDecimal.ZERO;
    }

    private BigDecimal expense(User u, LocalDate s, LocalDate e) {
        BigDecimal v = all(u)
                ? transactionRepository.sumExpense(s, e)
                : transactionRepository.sumExpenseByOwner(u.getId(), s, e);
        return v != null ? v : BigDecimal.ZERO;
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        User u = guard.user();
        Map<String, Object> stats = new HashMap<>();

        if (all(u)) {
            stats.put("totalUsers", userRepository.count());
            stats.put("totalCompanies", companyRepository.count());
            stats.put("totalTransactions", transactionRepository.count());
            stats.put("totalPayments", paymentRepository.count());
        } else {
            stats.put("totalUsers", 1);
            stats.put("totalCompanies", companyRepository.findByOwnerId(u.getId()).size());
            stats.put("totalTransactions", transactionRepository.countByAccountCompanyOwnerId(u.getId()));
            stats.put("totalPayments", paymentRepository.countByCompanyOwnerId(u.getId()));
        }

        LocalDate start = LocalDate.now().withDayOfMonth(1);
        LocalDate end = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());

        stats.put("monthlyIncome", income(u, start, end));
        stats.put("monthlyExpense", expense(u, start, end));

        return stats;
    }

    @GetMapping("/monthly-dynamics")
    public List<Map<String, Object>> getMonthlyDynamics() {
        User u = guard.user();
        List<Map<String, Object>> data = new ArrayList<>();
        String[] months = {"Янв", "Фев", "Мар", "Апр", "Май", "Июн", "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек"};

        for (int i = 5; i >= 0; i--) {
            LocalDate monthDate = LocalDate.now().minusMonths(i);
            LocalDate start = monthDate.withDayOfMonth(1);
            LocalDate end = monthDate.withDayOfMonth(monthDate.lengthOfMonth());

            Map<String, Object> monthMap = new HashMap<>();
            monthMap.put("month", months[monthDate.getMonthValue() - 1]);
            monthMap.put("income", income(u, start, end));
            monthMap.put("expense", expense(u, start, end));
            data.add(monthMap);
        }

        return data;
    }

    @GetMapping("/category-distribution")
    public List<Map<String, Object>> getCategoryDistribution() {
        User u = guard.user();

        List<Map<String, Object>> distribution = all(u)
                ? transactionRepository.findAllCategoryDistribution()
                : transactionRepository.findCategoryDistributionByOwner(u.getId());

        return distribution != null ? distribution : new ArrayList<>();
    }

    @GetMapping("/insights")
    public AnalyticsInsightsService.Insights getInsights(@RequestParam UUID companyId) {
        guard.readCompany(companyId);
        return insightsService.insights(companyId);
    }
}
