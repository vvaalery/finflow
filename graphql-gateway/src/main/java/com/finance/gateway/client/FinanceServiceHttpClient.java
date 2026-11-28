package com.finance.gateway.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.gateway.security.AuthenticatedUser;
import com.finance.graphqlapi.dto.GraphQLInput.*;
import com.finance.graphqlapi.dto.GraphQLView.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class FinanceServiceHttpClient implements CompanyServiceClient, PaymentServiceClient, ForecastServiceClient {
    private static final String API = "/internal/v1";
    private final RestClient http;
    private final ObjectMapper mapper;

    public FinanceServiceHttpClient(RestClient.Builder builder, ObjectMapper mapper, @Value("${services.finance-url}") String url) {
        this.http = builder.baseUrl(url).requestInterceptor((request, body, execution) -> {
            request.getHeaders().setBearerAuth(AuthenticatedUser.current().token());
            return execution.execute(request, body);
        }).build();
        this.mapper = mapper;
    }

    private <T> T get(String uri, Class<T> type, Object... vars) {
        return ServiceCall.run(mapper, () -> http.get().uri(API + uri, vars).retrieve().body(type));
    }

    private <T> List<T> getList(String uri, ParameterizedTypeReference<List<T>> type, Object... vars) {
        return ServiceCall.run(mapper, () -> http.get().uri(API + uri, vars).retrieve().body(type));
    }

    private MutationResult send(HttpMethod method, String uri, Object body, Object... vars) {
        return ServiceCall.run(mapper, () -> {
            RestClient.RequestBodySpec spec = http.method(method).uri(API + uri, vars);
            if (body != null) spec.body(body);
            return spec.retrieve().body(MutationResult.class);
        });
    }

    @Override public List<CompanyView> companies() { return getList("/companies", new ParameterizedTypeReference<List<CompanyView>>() {}); }
    @Override public CompanyView company(UUID id) { return get("/companies/{id}", CompanyView.class, id); }
    @Override public MutationResult createCompany(CompanyInput input, UUID ownerId) {
        return ServiceCall.run(mapper, () -> http.post()
                .uri(u -> u.path(API + "/companies").queryParamIfPresent("ownerId", Optional.ofNullable(ownerId)).build())
                .body(input).retrieve().body(MutationResult.class));
    }
    @Override public MutationResult updateCompany(UUID id, CompanyInput input) { return send(HttpMethod.PUT, "/companies/{id}", input, id); }
    @Override public MutationResult deleteCompany(UUID id) { return send(HttpMethod.DELETE, "/companies/{id}", null, id); }

    @Override public List<AccountView> accounts(UUID companyId) { return getList("/accounts?companyId={c}", new ParameterizedTypeReference<List<AccountView>>() {}, companyId); }
    @Override public AccountView account(UUID id) { return get("/accounts/{id}", AccountView.class, id); }
    @Override public MutationResult createAccount(AccountInput input) { return send(HttpMethod.POST, "/accounts", input); }
    @Override public MutationResult updateAccount(UUID id, AccountInput input) { return send(HttpMethod.PUT, "/accounts/{id}", input, id); }
    @Override public MutationResult deleteAccount(UUID id) { return send(HttpMethod.DELETE, "/accounts/{id}", null, id); }

    @Override public List<TransactionView> transactions(TransactionQuery query) {
        return ServiceCall.run(mapper, () -> http.post().uri(API + "/transactions/search").body(query)
                .retrieve().body(new ParameterizedTypeReference<List<TransactionView>>() {}));
    }
    @Override public TransactionView transaction(UUID id) { return get("/transactions/{id}", TransactionView.class, id); }
    @Override public MutationResult createTransaction(TransactionInput input) { return send(HttpMethod.POST, "/transactions", input); }
    @Override public MutationResult updateTransaction(UUID id, TransactionInput input) { return send(HttpMethod.PUT, "/transactions/{id}", input, id); }
    @Override public MutationResult deleteTransaction(UUID id) { return send(HttpMethod.DELETE, "/transactions/{id}", null, id); }
    @Override public AggregateView aggregate(UUID companyId, LocalDate from, LocalDate to) {
        return ServiceCall.run(mapper, () -> http.get().uri(u -> u.path(API + "/transactions/aggregate")
                .queryParam("companyId", companyId)
                .queryParamIfPresent("from", Optional.ofNullable(from))
                .queryParamIfPresent("to", Optional.ofNullable(to)).build()).retrieve().body(AggregateView.class));
    }

    @Override public List<PaymentView> payments(UUID companyId) { return getList("/payments?companyId={c}", new ParameterizedTypeReference<List<PaymentView>>() {}, companyId); }
    @Override public MutationResult createPayment(PaymentInput input) { return send(HttpMethod.POST, "/payments", input); }
    @Override public MutationResult updatePayment(UUID id, PaymentInput input) { return send(HttpMethod.PUT, "/payments/{id}", input, id); }
    @Override public MutationResult deletePayment(UUID id) { return send(HttpMethod.DELETE, "/payments/{id}", null, id); }
    @Override public List<PaymentRequestView> paymentRequests() { return getList("/payment-requests", new ParameterizedTypeReference<List<PaymentRequestView>>() {}); }
    @Override public PaymentRequestView paymentRequest(UUID id) { return get("/payment-requests/{id}", PaymentRequestView.class, id); }
    @Override public MutationResult createPaymentRequest(PaymentRequestInput input) { return send(HttpMethod.POST, "/payment-requests", input); }
    @Override public MutationResult approvePaymentRequest(UUID id) { return send(HttpMethod.POST, "/payment-requests/{id}/approve", null, id); }
    @Override public MutationResult rejectPaymentRequest(UUID id) { return send(HttpMethod.POST, "/payment-requests/{id}/reject", null, id); }

    @Override public List<ForecastView> forecasts(UUID companyId) { return getList("/forecasts?companyId={c}", new ParameterizedTypeReference<List<ForecastView>>() {}, companyId); }
    @Override public ForecastResultView generateForecast(GenerateForecastInput input) {
        return ServiceCall.run(mapper, () -> http.post().uri(API + "/forecasts/generate").body(input).retrieve().body(ForecastResultView.class));
    }
}
