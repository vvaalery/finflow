package com.finance.backend.service;

import com.finance.backend.entity.Account;
import com.finance.backend.entity.Transaction;
import com.finance.backend.repository.AccountRepository;
import com.finance.backend.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CashFlowHistoryService {
    public static final int MAX_MONTHS = 36;
    public static final String BASE_CURRENCY = "BYN";

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    public record History(YearMonth start, double[] inflow, double[] outflow, int excludedOtherCurrency,
                          Map<String, Double> expenseByCategory) {
        public int months() { return inflow.length; }
        public YearMonth end() { return start.plusMonths(Math.max(0, months() - 1)); }
        public double[] net() {
            double[] n = new double[inflow.length];
            for (int i = 0; i < n.length; i++) n[i] = inflow[i] - outflow[i];
            return n;
        }
    }

    @Transactional(readOnly = true)
    public History load(UUID companyId) {
        YearMonth last = YearMonth.now().minusMonths(1);
        YearMonth from = last.minusMonths(MAX_MONTHS - 1L);
        List<Transaction> list = transactionRepository.findByAccountCompanyIdAndDateBetween(companyId, from.atDay(1), last.atEndOfMonth());

        Map<YearMonth, double[]> sums = new HashMap<>();
        Map<String, Double> categories = new HashMap<>();
        YearMonth categoryFrom = last.minusMonths(11);
        int excluded = 0;
        for (Transaction t : list) {
            String currency = t.getAccount() == null ? null : t.getAccount().getCurrency();
            if (currency != null && !BASE_CURRENCY.equalsIgnoreCase(currency)) { excluded++; continue; }
            boolean income = "INCOME".equals(t.getTransactionType());
            boolean expense = "EXPENSE".equals(t.getTransactionType());
            if (!income && !expense) continue;
            YearMonth ym = YearMonth.from(t.getDate());
            double amount = t.getAmount() == null ? 0 : t.getAmount().abs().doubleValue();
            double[] acc = sums.computeIfAbsent(ym, k -> new double[2]);
            acc[income ? 0 : 1] += amount;
            if (expense && !ym.isBefore(categoryFrom)) {
                String c = t.getCategory() == null || t.getCategory().isBlank() ? "Без категории" : t.getCategory();
                categories.merge(c, amount, Double::sum);
            }
        }
        YearMonth first = null;
        for (YearMonth ym = from; !ym.isAfter(last); ym = ym.plusMonths(1)) {
            double[] v = sums.get(ym);
            if (v != null && (v[0] != 0 || v[1] != 0)) { first = ym; break; }
        }
        if (first == null) return new History(last, new double[0], new double[0], excluded, categories);
        int n = (int) java.time.temporal.ChronoUnit.MONTHS.between(first, last) + 1;
        double[] in = new double[n], out = new double[n];
        for (int i = 0; i < n; i++) {
            double[] v = sums.get(first.plusMonths(i));
            if (v != null) { in[i] = v[0]; out[i] = v[1]; }
        }
        return new History(first, in, out, excluded, categories);
    }

    @Transactional(readOnly = true)
    public BigDecimal baseCurrencyBalance(UUID companyId) {
        BigDecimal total = BigDecimal.ZERO;
        for (Account a : accountRepository.findByCompanyIdAndIsActiveTrue(companyId)) {
            if (a.getCurrency() == null || BASE_CURRENCY.equalsIgnoreCase(a.getCurrency())) total = total.add(a.getBalance());
        }
        return total;
    }
}
