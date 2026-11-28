package com.finance.backend.client;

import com.finance.backend.dto.response.ForecastResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

@Component
public class ForecastServiceClient {

    private final RestClient http;
    private final String internalToken;

    public ForecastServiceClient(
            RestClient.Builder builder,
            @Value("${services.forecast-url}") String url,
            @Value("${services.forecast-connect-timeout-ms:1000}") int connectTimeoutMs,
            @Value("${services.forecast-read-timeout-ms:10000}") int readTimeoutMs,
            @Value("${internal.service-token:}") String internalToken
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);

        this.internalToken = internalToken;
        this.http = builder.baseUrl(url).requestFactory(factory).build();
    }

    public record Command(UUID companyId, Integer months, String method, String scenario) {}

    public record Risk(String deficitMonth, String negativeNetMonth, double balance) {}

    public ForecastResponse generate(UUID companyId, int months, String method, String scenario) {
        return http.post()
                .uri("/internal/forecasts/generate")
                .header("X-Internal-Token", internalToken)
                .body(new Command(companyId, months, method, scenario))
                .retrieve()
                .body(ForecastResponse.class);
    }

    public List<ForecastResponse.ModelScore> compare(UUID companyId, String method) {
        List<ForecastResponse.ModelScore> result = http.post()
                .uri("/internal/forecasts/compare")
                .header("X-Internal-Token", internalToken)
                .body(new Command(companyId, 6, method, "BASE"))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        return result == null ? List.of() : result;
    }

    public ForecastResponse latest(UUID companyId) {
        return http.get()
                .uri("/internal/forecasts/{id}/latest", companyId)
                .header("X-Internal-Token", internalToken)
                .retrieve()
                .body(ForecastResponse.class);
    }

    public List<ForecastResponse> history(UUID companyId) {
        List<ForecastResponse> result = http.get()
                .uri("/internal/forecasts/{id}/history", companyId)
                .header("X-Internal-Token", internalToken)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        return result == null ? List.of() : result;
    }

    public Risk risk(UUID companyId) {
        return http.get()
                .uri("/internal/forecasts/{id}/risk", companyId)
                .header("X-Internal-Token", internalToken)
                .retrieve()
                .body(Risk.class);
    }
}
