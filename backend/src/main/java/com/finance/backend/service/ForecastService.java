package com.finance.backend.service;

import com.finance.backend.client.ForecastServiceClient;
import com.finance.backend.dto.response.ForecastResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ForecastService {
    public static final int MIN_HISTORY_MONTHS = 6;

    private final ForecastServiceClient client;

    public ForecastResponse generate(UUID companyId, int months, String method, String scenario) {
        return client.generate(companyId, months, method, scenario);
    }

    public ForecastResponse latestOrNull(UUID companyId) {
        try {
            return client.latest(companyId);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    public ForecastServiceClient.Risk risk(UUID companyId) {
        return client.risk(companyId);
    }
}
