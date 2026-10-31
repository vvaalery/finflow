package com.finance.backend.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record ForecastComparisonResponse(
        String trainingPeriod,
        String testingPeriod,
        int testedMonths,
        List<AlgorithmResult> results,
        List<MonthlyResult> monthlyResults
) {
    public record AlgorithmResult(
            String algorithm,
            BigDecimal incomeMae,
            BigDecimal incomeRmse,
            BigDecimal incomeWape,
            BigDecimal expenseMae,
            BigDecimal expenseRmse,
            BigDecimal expenseWape
    ) {}

    public record MonthlyResult(
            String month,
            BigDecimal actualIncome,
            BigDecimal actualExpenses,
            List<AlgorithmPrediction> predictions
    ) {}

    public record AlgorithmPrediction(
            String algorithm,
            BigDecimal predictedIncome,
            BigDecimal predictedExpenses
    ) {}
}
