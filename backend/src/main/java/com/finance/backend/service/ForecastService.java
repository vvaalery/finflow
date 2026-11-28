package com.finance.backend.service;

import com.finance.backend.analytics.ForecastMethod;
import com.finance.backend.analytics.Scenario;
import com.finance.backend.client.ForecastServiceClient;
import com.finance.backend.client.ForecastServiceClient.ForecastRequest;
import com.finance.backend.dto.response.ForecastResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ForecastService {
    public static final int MIN_HISTORY_MONTHS = 6;

    private final CashFlowHistoryService historyService;
    private final ForecastServiceClient forecastClient;

    public ForecastResponse generateForecast(
            UUID companyId,
            int months,
            String strategy,
            Double multiplier
    ) {
        return generateForecast(
                companyId,
                months,
                ForecastMethod.parse(strategy),
                legacyScenario(strategy, multiplier)
        );
    }

    public ForecastResponse generateForecast(
            UUID companyId,
            int months,
            ForecastMethod method,
            Scenario scenario
    ) {
        if (months < 1 || months > 24) {
            throw new IllegalArgumentException("Период прогноза: от 1 до 24 месяцев");
        }

        CashFlowHistoryService.History history = historyService.load(companyId);

        if (history.months() < MIN_HISTORY_MONTHS) {
            throw new IllegalArgumentException(
                    "Недостаточно истории для прогноза: нужно минимум "
                            + MIN_HISTORY_MONTHS
                            + " завершённых месяцев с операциями в BYN, сейчас — "
                            + history.months() + "."
            );
        }

        ForecastRequest request = new ForecastRequest(
                companyId,
                months,
                method.name(),
                scenario.name(),
                history.start(),
                history.inflow(),
                history.outflow(),
                history.excludedOtherCurrency(),
                history.expenseByCategory(),
                historyService.baseCurrencyBalance(companyId)
        );

        return forecastClient.generate(request);
    }

    public List<ForecastResponse.ModelScore> compareMethods(
            UUID companyId,
            ForecastMethod selected
    ) {
        CashFlowHistoryService.History history = historyService.load(companyId);

        if (history.months() < MIN_HISTORY_MONTHS) {
            return List.of();
        }

        ForecastRequest request = new ForecastRequest(
                companyId,
                6,
                selected.name(),
                Scenario.BASE.name(),
                history.start(),
                history.inflow(),
                history.outflow(),
                history.excludedOtherCurrency(),
                history.expenseByCategory(),
                null
        );

        return forecastClient.compare(request);
    }

    public ForecastResponse getLatestForecast(UUID companyId) {
        ForecastResponse response = getLatestForecastOrNull(companyId);

        if (response == null) {
            throw new NoSuchElementException("Прогнозы не найдены");
        }

        return response;
    }

    public ForecastResponse getLatestForecastOrNull(UUID companyId) {
        try {
            return forecastClient.latest(companyId);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    public List<ForecastResponse> getForecastHistory(UUID companyId) {
        return forecastClient.history(companyId);
    }

    private static Scenario legacyScenario(String strategy, Double multiplier) {
        if (strategy != null) {
            String value = strategy.toUpperCase(Locale.ROOT);

            if (value.contains("OPTIMISTIC")) {
                return Scenario.OPTIMISTIC;
            }

            if (value.contains("PESSIMISTIC")) {
                return Scenario.PESSIMISTIC;
            }
        }

        if (multiplier != null) {
            if (multiplier > 1) return Scenario.OPTIMISTIC;
            if (multiplier < 1) return Scenario.PESSIMISTIC;
        }

        return Scenario.BASE;
    }

    public static Scenario parseScenario(String raw) {
        if (raw == null || raw.isBlank()) {
            return Scenario.BASE;
        }

        try {
            return Scenario.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Scenario.BASE;
        }
    }
}
