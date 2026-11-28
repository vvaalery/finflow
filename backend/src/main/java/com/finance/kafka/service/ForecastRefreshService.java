package com.finance.kafka.service;

import com.finance.backend.analytics.ForecastMethod;
import com.finance.backend.dto.response.ForecastResponse;
import com.finance.backend.entity.CashFlowForecast;
import com.finance.backend.repository.CashFlowForecastRepository;
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

    private final CashFlowForecastRepository forecasts;
    private final CashFlowHistoryService history;
    private final ForecastService forecastService;
    private final OutboxEventService outbox;

    @Transactional
    public boolean refreshLatest(UUID companyId) {
        CashFlowForecast latest = forecasts.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .findFirst().orElse(null);

        if (latest == null) {
            log.info("Прогноз компании {} ещё не строился: обновлять нечего", companyId);
            return false;
        }

        if (latest.getCreatedAt() != null
                && latest.getCreatedAt().isAfter(LocalDateTime.now().minusSeconds(30))) {
            log.info("Прогноз компании {} построен менее 30 секунд назад: пропуск", companyId);
            return false;
        }

        if (history.load(companyId).months() < ForecastService.MIN_HISTORY_MONTHS) {
            log.info("Для компании {} недостаточно истории, чтобы перестроить прогноз", companyId);
            return false;
        }

        int months = (int) Math.min(24, Math.max(1, ChronoUnit.MONTHS.between(
                YearMonth.from(latest.getForecastPeriodStart()),
                YearMonth.from(latest.getForecastPeriodEnd())) + 1));

        ForecastResponse r = forecastService.generateForecast(companyId, months,
                ForecastMethod.parse(latest.getStrategyUsed()), ForecastService.parseScenario(latest.getScenario()));

        outbox.enqueue(EventTypes.FORECAST_GENERATED, r.getId(), companyId,
                "forecastId", r.getId(), "method", r.getMethod(), "scenario", r.getScenario(),
                "months", months, "reason", "auto-refresh");

        log.info("Прогноз компании {} перестроен автоматически: forecastId={}", companyId, r.getId());
        return true;
    }
}
