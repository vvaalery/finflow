package com.finance.graphqlapi.controller;

import com.finance.gateway.client.CompanyServiceClient;
import com.finance.gateway.client.ForecastServiceClient;
import com.finance.gateway.client.PaymentServiceClient;
import com.finance.graphqlapi.dto.GraphQLInput.*;
import com.finance.graphqlapi.dto.GraphQLView.*;
import jakarta.validation.Valid;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Controller
@Validated
public class FinanceGraphQLController {
    private static final String ANY = "hasAnyRole('ADMIN','FINANCIAL_MANAGER','DIRECTOR')";
    private static final String WRITE = "hasAnyRole('ADMIN','FINANCIAL_MANAGER')";
    private static final String ADMIN = "hasRole('ADMIN')";
    private static final String DECIDE = "hasAnyRole('ADMIN','DIRECTOR')";

    private final CompanyServiceClient companies;
    private final PaymentServiceClient payments;
    private final ForecastServiceClient forecasts;

    public FinanceGraphQLController(CompanyServiceClient companies, PaymentServiceClient payments, ForecastServiceClient forecasts) {
        this.companies = companies;
        this.payments = payments;
        this.forecasts = forecasts;
    }

    @QueryMapping @PreAuthorize(ANY) public List<CompanyView> companies() { return companies.companies(); }
    @QueryMapping @PreAuthorize(ANY) public CompanyView company(@Argument UUID id) { return companies.company(id); }
    @QueryMapping @PreAuthorize(ANY) public List<AccountView> accounts(@Argument UUID companyId) { return companies.accounts(companyId); }
    @QueryMapping @PreAuthorize(ANY) public AccountView account(@Argument UUID id) { return companies.account(id); }
    @QueryMapping @PreAuthorize(ANY)
    public List<TransactionView> transactions(@Argument UUID accountId, @Argument TransactionFilter filter, @Argument TransactionSort sort,
                                              @Argument Integer limit, @Argument Integer offset) {
        return companies.transactions(new TransactionQuery(accountId, filter, sort, limit, offset));
    }
    @QueryMapping @PreAuthorize(ANY) public TransactionView transaction(@Argument UUID id) { return companies.transaction(id); }
    @QueryMapping @PreAuthorize(ANY)
    public AggregateView transactionAggregate(@Argument UUID companyId, @Argument LocalDate from, @Argument LocalDate to) {
        return companies.aggregate(companyId, from, to);
    }
    @QueryMapping @PreAuthorize(ANY) public List<PaymentView> paymentCalendar(@Argument UUID companyId) { return payments.payments(companyId); }
    @QueryMapping @PreAuthorize(ANY) public List<PaymentRequestView> paymentRequests() { return payments.paymentRequests(); }
    @QueryMapping @PreAuthorize(ANY) public PaymentRequestView paymentRequest(@Argument UUID id) { return payments.paymentRequest(id); }
    @QueryMapping @PreAuthorize(ANY) public List<ForecastView> forecasts(@Argument UUID companyId) { return forecasts.forecasts(companyId); }

    @MutationMapping @PreAuthorize(WRITE)
    public MutationResult createCompany(@Argument @Valid CompanyInput input, @Argument UUID ownerId) { return companies.createCompany(input, ownerId); }
    @MutationMapping @PreAuthorize(WRITE)
    public MutationResult updateCompany(@Argument @Valid CompanyInput input, @Argument UUID id) { return companies.updateCompany(id, input); }
    @MutationMapping @PreAuthorize(ADMIN)
    public MutationResult deleteCompany(@Argument UUID id) { return companies.deleteCompany(id); }

    @MutationMapping @PreAuthorize(WRITE)
    public MutationResult createAccount(@Argument @Valid AccountInput input) { return companies.createAccount(input); }
    @MutationMapping @PreAuthorize(WRITE)
    public MutationResult updateAccount(@Argument @Valid AccountInput input, @Argument UUID id) { return companies.updateAccount(id, input); }
    @MutationMapping @PreAuthorize(ADMIN)
    public MutationResult deleteAccount(@Argument UUID id) { return companies.deleteAccount(id); }

    @MutationMapping @PreAuthorize(WRITE)
    public MutationResult createTransaction(@Argument @Valid TransactionInput input) { return companies.createTransaction(input); }
    @MutationMapping @PreAuthorize(WRITE)
    public MutationResult updateTransaction(@Argument @Valid TransactionInput input, @Argument UUID id) { return companies.updateTransaction(id, input); }
    @MutationMapping @PreAuthorize(ADMIN)
    public MutationResult deleteTransaction(@Argument UUID id) { return companies.deleteTransaction(id); }

    @MutationMapping @PreAuthorize(WRITE)
    public MutationResult createPayment(@Argument @Valid PaymentInput input) { return payments.createPayment(input); }
    @MutationMapping @PreAuthorize(WRITE)
    public MutationResult updatePayment(@Argument @Valid PaymentInput input, @Argument UUID id) { return payments.updatePayment(id, input); }
    @MutationMapping @PreAuthorize(ADMIN)
    public MutationResult deletePayment(@Argument UUID id) { return payments.deletePayment(id); }

    @MutationMapping @PreAuthorize(WRITE)
    public MutationResult createPaymentRequest(@Argument @Valid PaymentRequestInput input) { return payments.createPaymentRequest(input); }
    @MutationMapping @PreAuthorize(DECIDE)
    public MutationResult approvePaymentRequest(@Argument UUID id) { return payments.approvePaymentRequest(id); }
    @MutationMapping @PreAuthorize(DECIDE)
    public MutationResult rejectPaymentRequest(@Argument UUID id) { return payments.rejectPaymentRequest(id); }

    @MutationMapping @PreAuthorize(ANY)
    public ForecastResultView generateForecast(@Argument @Valid GenerateForecastInput input) { return forecasts.generateForecast(input); }
}
