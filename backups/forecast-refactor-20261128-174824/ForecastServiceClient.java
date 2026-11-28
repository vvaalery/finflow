package com.finance.backend.client;

import com.finance.backend.dto.response.ForecastResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class ForecastServiceClient {

    private final RestClient http;

    public ForecastServiceClient(
            RestClient.Builder builder,
            @Value("${services.forecast-url}") String url,
            @Value("${services.forecast-connect-timeout-ms:1000}") int connectTimeoutMs,
            @Value("${services.forecast-read-timeout-ms:10000}") int readTimeoutMs
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);

        this.http = builder
                .baseUrl(url)
                .requestFactory(factory)
                .build();
    }

    public ForecastResponse generate(ForecastRequest request) {
        return http.post()
                .uri("/internal/forecasts/generate")
                .body(request)
                .retrieve()
                .body(ForecastResponse.class);
    }

    public List<ForecastResponse.ModelScore> compare(ForecastRequest request) {
        List<ForecastResponse.ModelScore> result = http.post()
                .uri("/internal/forecasts/compare")
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        return result == null ? List.of() : result;
    }

    public ForecastResponse latest(UUID companyId) {
        return http.get()
                .uri("/internal/forecasts/{companyId}/latest", companyId)
                .retrieve()
                .body(ForecastResponse.class);
    }

    public List<ForecastResponse> history(UUID companyId) {
        List<ForecastResponse> result = http.get()
                .uri("/internal/forecasts/{companyId}/history", companyId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        return result == null ? List.of() : result;
    }

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
}
