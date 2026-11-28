package com.finance.forecast.service;

import com.finance.forecast.dto.ForecastRequest;
import com.finance.forecast.dto.ForecastResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ForecastPersistence {

    private final ForecastCalculationService calculation;
    private final ForecastOutbox outbox;
    private final ProcessedEventStore processed;

    @Transactional
    public ForecastResponse generateAndPublish(
            ForecastRequest request,
            String reason,
            String processedKey
    ) {
        ForecastResponse response = calculation.generate(request);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("forecastId", response.id().toString());
        payload.put("method", response.method());
        payload.put("scenario", response.scenario());
        payload.put("reason", reason);

        outbox.enqueue(
                "FORECAST_GENERATED",
                response.id(),
                request.companyId(),
                payload
        );

        if (processedKey != null) {
            processed.mark(processedKey);
        }

        return response;
    }
}
