package com.finance.kafka.service;

import com.finance.backend.analytics.ForecastMethod;
import com.finance.backend.analytics.Scenario;
import com.finance.backend.dto.response.ForecastResponse;
import com.finance.backend.service.CashFlowHistoryService;
import com.finance.backend.service.ForecastService;
import com.finance.kafka.event.EventTypes;
import com.finance.kafka.outbox.OutboxEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ForecastRefreshService {

    private static final int DEFAULT_MONTHS = 6;
    private static final int REFRESH_INTERVAL_SECONDS = 30;

    private final CashFlowHistoryService history;
    private final ForecastService forecastService;
    private final OutboxEventService outbox;

    @Transactional
    public boolean refreshLatest(UUID companyId) {
        ForecastResponse latest = forecastService.getLatestForecastOrNull(companyId);

        if (latest != null
                && latest.getCreatedAt() != null
                && latest.getCreatedAt().isAfter(
                        LocalDateTime.now().minusSeconds(REFRESH_INTERVAL_SECONDS))) {
            log.info("Прогноз компании {} построен менее 30 секунд назад: пропуск", companyId);
            return false;
        }

        if (history.load(companyId).months() < ForecastService.MIN_HISTORY_MONTHS) {
            log.info("Для компании {} недостаточно истории, чтобы построить прогноз", companyId);
            return false;
        }

        int months = latest == null ? DEFAULT_MONTHS : forecastMonths(latest);

        String methodName = latest == null
                ? null
                : latest.getMethod() != null && !latest.getMethod().isBlank()
                    ? latest.getMethod()
                    : latest.getStrategyUsed();

        ForecastMethod method = latest == null
                ? ForecastMethod.GRADIENT_BOOSTING
                : ForecastMethod.parse(methodName);

        Scenario scenario = latest == null
                ? Scenario.BASE
                : ForecastService.parseScenario(latest.getScenario());

        ForecastResponse response = forecastService.generateForecast(
                companyId,
                months,
                method,
                scenario
        );

        outbox.enqueue(
                EventTypes.FORECAST_GENERATED,
                response.getId(),
                companyId,
                "forecastId", response.getId(),
                "method", response.getMethod(),
                "scenario", response.getScenario(),
                "months", months,
                "reason", "auto-refresh"
        );

        log.info(
                "Прогноз компании {} построен автоматически: forecastId={}",
                companyId,
                response.getId()
        );

        return true;
    }

    private int forecastMonths(ForecastResponse forecast) {
        if (forecast.getForecastPeriodStart() == null
                || forecast.getForecastPeriodEnd() == null) {
            return DEFAULT_MONTHS;
        }

        long months = ChronoUnit.MONTHS.between(
                YearMonth.from(forecast.getForecastPeriodStart()),
                YearMonth.from(forecast.getForecastPeriodEnd())
        ) + 1;

        return (int) Math.min(24, Math.max(1, months));
    }
}
