package com.finance.forecast.service;

import com.finance.forecast.analytics.ForecastEngine;
import com.finance.forecast.analytics.ForecastMethod;
import com.finance.forecast.analytics.Scenario;
import com.finance.forecast.client.CompanyBalanceClient;
import com.finance.forecast.client.FinanceClient;
import com.finance.forecast.dto.ForecastRequest;
import com.finance.forecast.dto.ForecastResponse;
import com.finance.forecast.dto.RiskResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ForecastOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(ForecastOrchestrator.class);
    private static final int MIN = ForecastCalculationService.MIN_HISTORY_MONTHS;

    private final FinanceClient finance;
    private final CompanyBalanceClient balances;
    private final ForecastCalculationService calculation;
    private final ForecastPersistence persistence;
    private final ProcessedEventStore processed;

    public ForecastResponse generate(
            UUID companyId,
            Integer months,
            String method,
            String scenario,
            String reason
    ) {
        int m = months == null ? 6 : months;

        if (m < 1 || m > 24) {
            throw new IllegalArgumentException("Период прогноза: от 1 до 24 месяцев");
        }

        FinanceClient.History history = finance.history(companyId);
        requireHistory(history);

        return persistence.generateAndPublish(
                request(companyId, m, method, scenario, history),
                reason,
                null
        );
    }

    public List<ForecastResponse.ModelScore> compare(UUID companyId, String method) {
        FinanceClient.History history = finance.history(companyId);

        if (history.inflow().length < MIN) {
            return List.of();
        }

        return calculation.compare(
                new ForecastRequest(
                        companyId,
                        6,
                        method,
                        "BASE",
                        history.start(),
                        history.inflow(),
                        history.outflow(),
                        history.excludedOtherCurrency(),
                        history.expenseByCategory(),
                        null
                )
        );
    }

    public RiskResponse risk(UUID companyId) {
        FinanceClient.History history = finance.history(companyId);
        requireHistory(history);

        ForecastEngine.Result result = ForecastEngine.forecast(
                ForecastMethod.GRADIENT_BOOSTING,
                Scenario.BASE,
                history.inflow(),
                history.outflow(),
                history.start(),
                3
        );

        double balance = balances.baseCurrencyBalance(companyId).doubleValue();
        double running = balance;

        YearMonth deficit = null;
        YearMonth negativeNet = null;

        for (ForecastEngine.Point point : result.points()) {
            running += point.net();

            if (running < 0 && deficit == null) {
                deficit = point.month();
            }

            if (point.net() < 0 && negativeNet == null) {
                negativeNet = point.month();
            }
        }

        return new RiskResponse(
                deficit == null ? null : deficit.toString(),
                negativeNet == null ? null : negativeNet.toString(),
                balance
        );
    }

    public void refreshFromEvent(UUID companyId, String processedKey) {
        if (processed.exists(processedKey)) {
            log.info("Событие {} уже обработано", processedKey);
            return;
        }

        FinanceClient.History history = finance.history(companyId);

        if (history.inflow().length < MIN) {
            log.info(
                    "Для компании {} недостаточно истории ({} мес.): прогноз не строится",
                    companyId,
                    history.inflow().length
            );

            processed.mark(processedKey);
            return;
        }

        ForecastResponse latest = latestOrNull(companyId);

        int months = latest == null ? 6 : monthsOf(latest);

        String method = latest == null
                ? "GRADIENT_BOOSTING"
                : latest.method() != null
                    ? latest.method()
                    : latest.strategyUsed();

        String scenario = latest == null ? "BASE" : latest.scenario();

        persistence.generateAndPublish(
                request(companyId, months, method, scenario, history),
                "auto-refresh",
                processedKey
        );

        log.info(
                "Прогноз компании {} {}",
                companyId,
                latest == null ? "создан автоматически" : "перестроен автоматически"
        );
    }

    public static String legacyScenario(String strategy, Double multiplier) {
        if (strategy != null) {
            String value = strategy.toUpperCase(Locale.ROOT);

            if (value.contains("OPTIMISTIC")) {
                return "OPTIMISTIC";
            }

            if (value.contains("PESSIMISTIC")) {
                return "PESSIMISTIC";
            }
        }

        if (multiplier != null) {
            if (multiplier > 1) return "OPTIMISTIC";
            if (multiplier < 1) return "PESSIMISTIC";
        }

        return "BASE";
    }

    private ForecastRequest request(
            UUID companyId,
            int months,
            String method,
            String scenario,
            FinanceClient.History history
    ) {
        return new ForecastRequest(
                companyId,
                months,
                method,
                scenario,
                history.start(),
                history.inflow(),
                history.outflow(),
                history.excludedOtherCurrency(),
                history.expenseByCategory(),
                balances.baseCurrencyBalance(companyId)
        );
    }

    private void requireHistory(FinanceClient.History history) {
        if (history.inflow().length < MIN) {
            throw new IllegalArgumentException(
                    "Недостаточно истории для прогноза: нужно минимум "
                            + MIN
                            + " завершённых месяцев с операциями в BYN, сейчас — "
                            + history.inflow().length
                            + "."
            );
        }
    }

    private ForecastResponse latestOrNull(UUID companyId) {
        try {
            return calculation.latest(companyId);
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    private int monthsOf(ForecastResponse forecast) {
        if (forecast.forecastPeriodStart() == null
                || forecast.forecastPeriodEnd() == null) {
            return 6;
        }

        long months = ChronoUnit.MONTHS.between(
                YearMonth.from(forecast.forecastPeriodStart()),
                YearMonth.from(forecast.forecastPeriodEnd())
        ) + 1;

        return (int) Math.min(24, Math.max(1, months));
    }
}
