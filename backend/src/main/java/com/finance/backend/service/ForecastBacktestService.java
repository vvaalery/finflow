package com.finance.backend.service;

import com.finance.backend.dto.response.ForecastComparisonResponse;
import com.finance.backend.entity.Transaction;
import com.finance.backend.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ForecastBacktestService {

    private final TransactionRepository transactionRepository;

    private static final int SCALE = 2;

    /**
     * Сравнение алгоритмов на последних 12 полных месяцах.
     *
     * Каждый месяц прогнозируется только по истории,
     * которая была доступна до начала этого месяца.
     *
     * Результаты backtest НЕ сохраняются в cash_flow_forecasts.
     */
    public ForecastComparisonResponse compare(UUID companyId) {

        LocalDate today = LocalDate.now();

        // Последний полностью завершённый месяц.
        LocalDate lastTestMonth = today.withDayOfMonth(1).minusMonths(1);

        // Тестируем последние 12 полных месяцев.
        LocalDate firstTestMonth = lastTestMonth.minusMonths(11);

        // История для самого первого тестового прогноза.
        // Берём 12 месяцев до начала тестового периода.
        LocalDate trainingStart = firstTestMonth.minusMonths(12);

        // Загружаем данные только один раз.
        List<Transaction> transactions =
                transactionRepository.findByAccountCompanyIdAndDateBetween(
                        companyId,
                        trainingStart,
                        lastTestMonth.withDayOfMonth(
                                lastTestMonth.lengthOfMonth()
                        )
                );

        Map<LocalDate, MonthlyActual> actuals = buildMonthlyActuals(
                transactions,
                trainingStart,
                lastTestMonth
        );

        List<ForecastComparisonResponse.MonthlyResult> monthlyResults =
                new ArrayList<>();

        List<MetricAccumulator> accumulators = new ArrayList<>();

        accumulators.add(new MetricAccumulator("MOVING_AVERAGE"));
        accumulators.add(new MetricAccumulator("LINEAR_REGRESSION"));
        accumulators.add(new MetricAccumulator("GRADIENT_BOOSTING"));

        /*
         * Walk-forward validation:
         *
         * Для каждого месяца:
         * 1. берём только предыдущие месяцы;
         * 2. строим прогноз;
         * 3. сравниваем с фактом;
         * 4. переходим к следующему месяцу.
         *
         * Поэтому алгоритм не видит будущие значения заранее.
         */
        for (int monthIndex = 0; monthIndex < 12; monthIndex++) {

            LocalDate month = firstTestMonth.plusMonths(monthIndex);

            List<BigDecimal> incomeHistory =
                    getHistory(actuals, trainingStart, month.minusMonths(1), true);

            List<BigDecimal> expenseHistory =
                    getHistory(actuals, trainingStart, month.minusMonths(1), false);

            MonthlyActual actual = actuals.getOrDefault(
                    month,
                    new MonthlyActual(BigDecimal.ZERO, BigDecimal.ZERO)
            );

            List<ForecastComparisonResponse.AlgorithmPrediction> predictions =
                    new ArrayList<>();

            for (MetricAccumulator accumulator : accumulators) {

                double predictedIncome =
                        projectPoint(
                                incomeHistory,
                                1,
                                accumulator.algorithm
                        );

                double predictedExpenses =
                        projectPoint(
                                expenseHistory,
                                1,
                                accumulator.algorithm
                        );

                BigDecimal income = money(predictedIncome);
                BigDecimal expenses = money(predictedExpenses);

                predictions.add(
                        new ForecastComparisonResponse.AlgorithmPrediction(
                                accumulator.algorithm,
                                income,
                                expenses
                        )
                );

                accumulator.addIncome(
                        actual.income.doubleValue(),
                        predictedIncome
                );

                accumulator.addExpense(
                        actual.expenses.doubleValue(),
                        predictedExpenses
                );
            }

            monthlyResults.add(
                    new ForecastComparisonResponse.MonthlyResult(
                            month.toString(),
                            actual.income,
                            actual.expenses,
                            predictions
                    )
            );
        }

        List<ForecastComparisonResponse.AlgorithmResult> results =
                accumulators.stream()
                        .map(MetricAccumulator::toResult)
                        .toList();

        return new ForecastComparisonResponse(
                trainingStart.toString() + " — " +
                        firstTestMonth.minusMonths(1).toString(),

                firstTestMonth.toString() + " — " +
                        lastTestMonth.toString(),

                12,

                results,
                monthlyResults
        );
    }

    /**
     * Собираем фактические доходы/расходы по месяцам.
     */
    private Map<LocalDate, MonthlyActual> buildMonthlyActuals(
            List<Transaction> transactions,
            LocalDate start,
            LocalDate end
    ) {

        Map<LocalDate, MonthlyActual> result = new LinkedHashMap<>();

        LocalDate current = start.withDayOfMonth(1);

        while (!current.isAfter(end.withDayOfMonth(1))) {

            LocalDate month = current;

            BigDecimal income =
                    transactions.stream()
                            .filter(t -> "INCOME".equals(t.getTransactionType()))
                            .filter(t -> t.getDate().getYear() == month.getYear())
                            .filter(t -> t.getDate().getMonthValue() == month.getMonthValue())
                            .map(Transaction::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal expenses =
                    transactions.stream()
                            .filter(t -> "EXPENSE".equals(t.getTransactionType()))
                            .filter(t -> t.getDate().getYear() == month.getYear())
                            .filter(t -> t.getDate().getMonthValue() == month.getMonthValue())
                            .map(Transaction::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

            result.put(month, new MonthlyActual(income, expenses));

            current = current.plusMonths(1);
        }

        return result;
    }

    /**
     * Получаем историю до прогнозируемого месяца.
     */
    private List<BigDecimal> getHistory(
            Map<LocalDate, MonthlyActual> actuals,
            LocalDate start,
            LocalDate end,
            boolean income
    ) {

        List<BigDecimal> result = new ArrayList<>();

        LocalDate current = start.withDayOfMonth(1);
        LocalDate finish = end.withDayOfMonth(1);

        while (!current.isAfter(finish)) {

            MonthlyActual actual =
                    actuals.getOrDefault(
                            current,
                            new MonthlyActual(
                                    BigDecimal.ZERO,
                                    BigDecimal.ZERO
                            )
                    );

            result.add(
                    income
                            ? actual.income
                            : actual.expenses
            );

            current = current.plusMonths(1);
        }

        return result;
    }

    private double projectPoint(
            List<BigDecimal> history,
            int step,
            String algorithm
    ) {

        if (history == null || history.isEmpty()) {
            return 0;
        }

        if (history.stream()
                .allMatch(v -> v.compareTo(BigDecimal.ZERO) == 0)) {
            return 0;
        }

        return switch (algorithm) {

            case "MOVING_AVERAGE" ->
                    movingAverage(history);

            case "LINEAR_REGRESSION" ->
                    linearPoint(history, step);

            default ->
                    gradientBoostingPoint(history, step);
        };
    }

    /**
     * Скользящее среднее.
     */
    private double movingAverage(List<BigDecimal> history) {

        return history.stream()
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0);
    }

    /**
     * Линейная регрессия.
     */
    private double linearPoint(
            List<BigDecimal> history,
            int step
    ) {

        int n = history.size();

        if (n == 1) {
            return Math.max(
                    0,
                    history.get(0).doubleValue()
            );
        }

        double sx = 0;
        double sy = 0;
        double sxy = 0;
        double sx2 = 0;

        for (int i = 0; i < n; i++) {

            double x = i + 1;
            double y = history.get(i).doubleValue();

            sx += x;
            sy += y;
            sxy += x * y;
            sx2 += x * x;
        }

        double denominator =
                n * sx2 - sx * sx;

        if (denominator == 0) {
            return movingAverage(history);
        }

        double slope =
                (n * sxy - sx * sy) / denominator;

        double intercept =
                (sy - slope * sx) / n;

        return Math.max(
                0,
                slope * (n + step) + intercept
        );
    }

    /**
     * Gradient Boosting.
     *
     * Здесь реализована та же логика,
     * которая используется в ForecastService:
     * 40 неглубоких регрессионных деревьев
     * + линейный тренд.
     */
    private double gradientBoostingPoint(
            List<BigDecimal> history,
            int step
    ) {

        int n = history.size();

        if (n < 2) {
            return movingAverage(history);
        }

        double[] y = new double[n];

        for (int i = 0; i < n; i++) {
            y[i] = history.get(i).doubleValue();
        }

        double base =
                Arrays.stream(y)
                        .average()
                        .orElse(0);

        double[] predictions = new double[n];

        Arrays.fill(
                predictions,
                base
        );

        List<Stump> ensemble = new ArrayList<>();

        double learningRate = 0.05;

        for (int iteration = 0;
             iteration < 40;
             iteration++) {

            double[] residuals = new double[n];

            for (int i = 0; i < n; i++) {

                residuals[i] =
                        y[i] - predictions[i];
            }

            Stump best = null;

            double bestLoss =
                    Double.POSITIVE_INFINITY;

            for (int threshold = 1;
                 threshold < n;
                 threshold++) {

                double leftSum = 0;
                double rightSum = 0;

                int leftCount = 0;
                int rightCount = 0;

                for (int i = 0; i < n; i++) {

                    if (i < threshold) {

                        leftSum += residuals[i];
                        leftCount++;

                    } else {

                        rightSum += residuals[i];
                        rightCount++;
                    }
                }

                double leftValue =
                        leftCount == 0
                                ? 0
                                : leftSum / leftCount;

                double rightValue =
                        rightCount == 0
                                ? 0
                                : rightSum / rightCount;

                double loss = 0;

                for (int i = 0; i < n; i++) {

                    double prediction =
                            i < threshold
                                    ? leftValue
                                    : rightValue;

                    double error =
                            residuals[i] - prediction;

                    loss += error * error;
                }

                if (loss < bestLoss) {

                    bestLoss = loss;

                    best = new Stump(
                            threshold,
                            leftValue,
                            rightValue
                    );
                }
            }

            if (best == null) {
                break;
            }

            ensemble.add(best);

            for (int i = 0; i < n; i++) {

                predictions[i] +=
                        learningRate *
                                best.predict(i);
            }
        }

        int futureIndex =
                n + step - 1;

        double treePrediction = base;

        for (Stump tree : ensemble) {

            treePrediction +=
                    learningRate *
                            tree.predict(futureIndex);
        }

        double trendPrediction =
                linearPoint(history, step);

        return Math.max(
                0,
                treePrediction * 0.70 +
                        trendPrediction * 0.30
        );
    }

    private BigDecimal money(double value) {

        if (Double.isNaN(value)
                || Double.isInfinite(value)) {

            return BigDecimal.ZERO
                    .setScale(SCALE);
        }

        return BigDecimal.valueOf(
                        Math.max(0, value)
                )
                .setScale(
                        SCALE,
                        RoundingMode.HALF_UP
                );
    }

    private record MonthlyActual(
            BigDecimal income,
            BigDecimal expenses
    ) {}

    private record Stump(
            int threshold,
            double left,
            double right
    ) {

        double predict(int x) {

            return x < threshold
                    ? left
                    : right;
        }
    }

    /**
     * Накопление ошибок одного алгоритма.
     */
    private static class MetricAccumulator {

        private final String algorithm;

        private final List<Double> incomeErrors =
                new ArrayList<>();

        private final List<Double> incomeSquaredErrors =
                new ArrayList<>();

        private final List<Double> incomeActuals =
                new ArrayList<>();

        private final List<Double> expenseErrors =
                new ArrayList<>();

        private final List<Double> expenseSquaredErrors =
                new ArrayList<>();

        private final List<Double> expenseActuals =
                new ArrayList<>();

        private MetricAccumulator(String algorithm) {
            this.algorithm = algorithm;
        }

        private void addIncome(
                double actual,
                double predicted
        ) {

            double error =
                    Math.abs(actual - predicted);

            incomeErrors.add(error);

            incomeSquaredErrors.add(
                    Math.pow(
                            actual - predicted,
                            2
                    )
            );

            incomeActuals.add(actual);
        }

        private void addExpense(
                double actual,
                double predicted
        ) {

            double error =
                    Math.abs(actual - predicted);

            expenseErrors.add(error);

            expenseSquaredErrors.add(
                    Math.pow(
                            actual - predicted,
                            2
                    )
            );

            expenseActuals.add(actual);
        }

        private ForecastComparisonResponse.AlgorithmResult toResult() {

            return new ForecastComparisonResponse.AlgorithmResult(
                    algorithm,
                    money(mae(incomeErrors)),
                    money(rmse(incomeSquaredErrors)),
                    money(wape(
                            incomeErrors,
                            incomeActuals
                    )),
                    money(mae(expenseErrors)),
                    money(rmse(expenseSquaredErrors)),
                    money(wape(
                            expenseErrors,
                            expenseActuals
                    ))
            );
        }

        private double mae(List<Double> errors) {

            if (errors.isEmpty()) {
                return 0;
            }

            return errors.stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0);
        }

        private double rmse(List<Double> squaredErrors) {

            if (squaredErrors.isEmpty()) {
                return 0;
            }

            return Math.sqrt(
                    squaredErrors.stream()
                            .mapToDouble(Double::doubleValue)
                            .average()
                            .orElse(0)
            );
        }

        private double wape(
                List<Double> errors,
                List<Double> actuals
        ) {

            double numerator =
                    errors.stream()
                            .mapToDouble(Double::doubleValue)
                            .sum();

            double denominator =
                    actuals.stream()
                            .mapToDouble(Double::doubleValue)
                            .map(Math::abs)
                            .sum();

            if (denominator == 0) {
                return 0;
            }

            return numerator / denominator * 100.0;
        }

        private static BigDecimal money(double value) {

            if (Double.isNaN(value)
                    || Double.isInfinite(value)) {

                return BigDecimal.ZERO
                        .setScale(SCALE);
            }

            return BigDecimal.valueOf(value)
                    .setScale(
                            SCALE,
                            RoundingMode.HALF_UP
                    );
        }
    }
}
