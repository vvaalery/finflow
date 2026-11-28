package com.finance.forecast.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ForecastResponse(
        UUID id,
        BigDecimal totalProjectedInflow,
        BigDecimal totalProjectedOutflow,
        BigDecimal netCashFlow,
        String strategyUsed,
        String period,
        List<ForecastPoint> items,
        String method,
        String methodLabel,
        String methodFamily,
        String scenario,
        BigDecimal currentBalance,
        BigDecimal projectedClosingBalance,
        List<HistoryPoint> history,
        Quality quality,
        List<ModelScore> comparison,
        List<String> warnings,
        Map<String, Object> modelParams,
        UUID companyId,
        LocalDate forecastPeriodStart,
        LocalDate forecastPeriodEnd,
        LocalDateTime createdAt
) {
    public record ForecastPoint(
            LocalDate date,
            BigDecimal inflow,
            BigDecimal outflow,
            BigDecimal netCashFlow,
            BigDecimal inflowLow,
            BigDecimal inflowHigh,
            BigDecimal outflowLow,
            BigDecimal outflowHigh,
            BigDecimal netLow,
            BigDecimal netHigh,
            BigDecimal projectedBalance
    ) {}

    public record HistoryPoint(
            LocalDate date,
            BigDecimal inflow,
            BigDecimal outflow,
            BigDecimal netCashFlow
    ) {}

    public record Quality(
            boolean available,
            int windows,
            Double mae,
            Double rmse,
            Double wape,
            String note
    ) {}

    public record ModelScore(
            String method,
            String label,
            String family,
            boolean selected,
            boolean best,
            Double wape,
            Double mae,
            Double rmse
    ) {}
}
