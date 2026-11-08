package com.finance.backend.controller;

import com.finance.backend.repository.TransactionRepository;
import com.finance.backend.repository.UserRepository;
import com.finance.backend.repository.CompanyRepository;
import com.finance.backend.repository.PaymentCalendarRepository;
import com.finance.backend.service.AnalyticsInsightsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

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

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalUsers", userRepository.count());
        stats.put("totalCompanies", companyRepository.count());
        stats.put("totalTransactions", transactionRepository.count());
        stats.put("totalPayments", paymentRepository.count());
        
        LocalDate start = LocalDate.now().withDayOfMonth(1);
        LocalDate end = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());
        
        BigDecimal income = transactionRepository.sumIncome(start, end);
        BigDecimal expense = transactionRepository.sumExpense(start, end);
        
        stats.put("monthlyIncome", income != null ? income : BigDecimal.ZERO);
        stats.put("monthlyExpense", expense != null ? expense : BigDecimal.ZERO);
        
        return stats;
    }

    @GetMapping("/monthly-dynamics")
    public List<Map<String, Object>> getMonthlyDynamics() {
        List<Map<String, Object>> data = new ArrayList<>();
        String[] months = {"Янв", "Фев", "Мар", "Апр", "Май", "Июн", "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек"};
        
        for (int i = 5; i >= 0; i--) {
            LocalDate monthDate = LocalDate.now().minusMonths(i);
            LocalDate start = monthDate.withDayOfMonth(1);
            LocalDate end = monthDate.withDayOfMonth(monthDate.lengthOfMonth());
            
            BigDecimal inc = transactionRepository.sumIncome(start, end);
            BigDecimal exp = transactionRepository.sumExpense(start, end);
            
            Map<String, Object> monthMap = new HashMap<>();
            monthMap.put("month", months[monthDate.getMonthValue() - 1]);
            monthMap.put("income", inc != null ? inc : 0);
            monthMap.put("expense", exp != null ? exp : 0);
            data.add(monthMap);
        }
        return data;
    }

    @GetMapping("/category-distribution")
    public List<Map<String, Object>> getCategoryDistribution() {
        List<Map<String, Object>> distribution = transactionRepository.findAllCategoryDistribution();
        return distribution != null ? distribution : new ArrayList<>();
    }

    @GetMapping("/insights")
    public AnalyticsInsightsService.Insights getInsights(@RequestParam UUID companyId) {
        return insightsService.insights(companyId);
    }
}
