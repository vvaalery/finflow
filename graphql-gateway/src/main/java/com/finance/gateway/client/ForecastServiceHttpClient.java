package com.finance.gateway.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.gateway.security.AuthenticatedUser;
import com.finance.graphqlapi.dto.GraphQLInput.GenerateForecastInput;
import com.finance.graphqlapi.dto.GraphQLView.ForecastPointView;
import com.finance.graphqlapi.dto.GraphQLView.ForecastResultView;
import com.finance.graphqlapi.dto.GraphQLView.ForecastView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class ForecastServiceHttpClient implements ForecastServiceClient {
    private final RestClient http;
    private final ObjectMapper mapper;

    public ForecastServiceHttpClient(
            RestClient.Builder builder,
            ObjectMapper mapper,
            @Value("${services.forecast-url}") String url
    ) {
        this.http = builder.baseUrl(url)
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setBearerAuth(AuthenticatedUser.current().token());
                    return execution.execute(request, body);
                })
                .build();
        this.mapper = mapper;
    }

    @Override
    public List<ForecastView> forecasts(java.util.UUID companyId) {
        return ServiceCall.run(mapper, () -> {
            List<ForecastData> forecasts = http.get()
                    .uri(uri -> uri.path("/api/forecast/history")
                            .queryParam("companyId", companyId)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<ForecastData>>() {});

            if (forecasts == null) return List.of();

            return forecasts.stream()
                    .map(f -> new ForecastView(
                            f.id(),
                            f.companyId(),
                            f.forecastPeriodStart(),
                            f.forecastPeriodEnd(),
                            f.totalProjectedInflow(),
                            f.totalProjectedOutflow(),
                            f.netCashFlow(),
                            f.strategyUsed(),
                            f.createdAt()
                    ))
                    .toList();
        });
    }

    @Override
    public ForecastResultView generateForecast(GenerateForecastInput input) {
        return ServiceCall.run(mapper, () -> {
            ForecastData forecast = http.get()
                    .uri(uri -> {
                        var builder = uri.path("/api/forecast/generate")
                                .queryParam("companyId", input.companyId());

                        if (input.months() != null) {
                            builder.queryParam("months", input.months());
                        }
                        if (input.method() != null && !input.method().isBlank()) {
                            builder.queryParam("method", input.method());
                        }
                        if (input.scenario() != null && !input.scenario().isBlank()) {
                            builder.queryParam("scenario", input.scenario());
                        }

                        return builder.build();
                    })
                    .retrieve()
                    .body(ForecastData.class);

            if (forecast == null) {
                throw new ServiceUnavailableException("Forecast Service вернул пустой ответ");
            }

            List<ForecastPointView> items = forecast.items() == null
                    ? List.of()
                    : forecast.items().stream()
                    .map(p -> new ForecastPointView(
                            p.date(),
                            p.inflow(),
                            p.outflow(),
                            p.netCashFlow(),
                            p.inflowLow(),
                            p.inflowHigh(),
                            p.outflowLow(),
                            p.outflowHigh(),
                            p.netLow(),
                            p.netHigh(),
                            p.projectedBalance()
                    ))
                    .toList();

            return new ForecastResultView(
                    forecast.id(),
                    forecast.companyId(),
                    forecast.method(),
                    forecast.scenario(),
                    forecast.forecastPeriodStart(),
                    forecast.forecastPeriodEnd(),
                    forecast.totalProjectedInflow(),
                    forecast.totalProjectedOutflow(),
                    forecast.netCashFlow(),
                    forecast.currentBalance(),
                    forecast.projectedClosingBalance(),
                    items,
                    forecast.warnings() == null ? List.of() : forecast.warnings()
            );
        });
    }

    private record ForecastData(
            java.util.UUID id,
            java.util.UUID companyId,
            BigDecimal totalProjectedInflow,
            BigDecimal totalProjectedOutflow,
            BigDecimal netCashFlow,
            String strategyUsed,
            String method,
            String scenario,
            BigDecimal currentBalance,
            BigDecimal projectedClosingBalance,
            List<ForecastPointData> items,
            List<String> warnings,
            LocalDate forecastPeriodStart,
            LocalDate forecastPeriodEnd,
            LocalDateTime createdAt
    ) {}

    private record ForecastPointData(
            LocalDate date,
            BigDecimal inflow,
            BigDecimal outflow,
            BigDecimal netCashFlow,
            BigDecimal inflowLow,
            BigDecimal inflowHigh,
            BigDecimal outflowLow,
            BigDecimal outflowHigh,
            BigDecimal netLow,
            BigDecimal netHigh,
            BigDecimal projectedBalance
    ) {}
}
