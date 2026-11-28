package com.finance.forecast.client;

import com.finance.forecast.config.CorrelationFilter;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;

@Component
public class FinanceClient {

    public record History(
            YearMonth start,
            double[] inflow,
            double[] outflow,
            int excludedOtherCurrency,
            Map<String, Double> expenseByCategory
    ) {}

    private final RestClient http;
    private final String token;

    public FinanceClient(
            RestClient.Builder builder,
            @Value("${services.finance-url}") String url,
            @Value("${services.internal-token:}") String token,
            @Value("${services.connect-timeout-ms:1000}") int connectTimeoutMs,
            @Value("${services.read-timeout-ms:10000}") int readTimeoutMs
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);

        this.token = token;
        this.http = builder
                .baseUrl(url)
                .requestFactory(factory)
                .requestInterceptor((request, body, execution) -> {
                    String id = MDC.get(CorrelationFilter.MDC_KEY);
                    if (id != null) {
                        request.getHeaders().set(CorrelationFilter.HEADER, id);
                    }
                    return execution.execute(request, body);
                })
                .build();
    }

    public History history(UUID companyId) {
        History history = http.get()
                .uri("/internal/v1/companies/{id}/cash-flow-history", companyId)
                .header("X-Internal-Token", token)
                .retrieve()
                .body(History.class);

        if (history == null) {
            throw new IllegalStateException("Finance Service вернул пустую историю");
        }

        return history;
    }

    public void requireAccess(UUID companyId, String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Требуется аутентификация"
            );
        }

        try {
            http.get()
                    .uri("/internal/v1/companies/{id}/access", companyId)
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            int code = e.getStatusCode().value();

            throw new ResponseStatusException(
                    e.getStatusCode(),
                    code == 404
                            ? "Компания не найдена"
                            : code == 401
                                ? "Требуется аутентификация"
                                : "Нет доступа"
            );
        }
    }
}
