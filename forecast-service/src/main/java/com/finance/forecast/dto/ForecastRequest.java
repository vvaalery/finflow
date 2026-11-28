package com.finance.forecast.dto;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;

public record ForecastRequest(
        UUID companyId,
        Integer months,
        String method,
        String scenario,
        YearMonth historyStart,
        double[] inflow,
        double[] outflow,
        int excludedOtherCurrency,
        Map<String, Double> expenseByCategory,
        BigDecimal baseCurrencyBalance
) {}
