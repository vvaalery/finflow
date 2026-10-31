
package com.finance.graphqlapi.controller;

import com.finance.graphqlapi.dto.GraphQLInput;
import com.finance.graphqlapi.dto.GraphQLView;
import com.finance.graphqlapi.client.FinanceServiceClient;
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
public class GraphQLQueryController {

    private final FinanceServiceClient service;

    public GraphQLQueryController(FinanceServiceClient service) {
        this.service = service;
    }

    

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public List<GraphQLView.UserView> users() {
        return service.users();
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public GraphQLView.UserView user(@Argument UUID id) {
        return service.user(id);
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public List<GraphQLView.CompanyView> companies() {
        return service.companies();
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public GraphQLView.CompanyView company(@Argument UUID id) {
        return service.company(id);
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public List<GraphQLView.AccountView> accounts(@Argument UUID companyId) {
        return service.accounts(companyId);
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public GraphQLView.AccountView account(@Argument UUID id) {
        return service.account(id);
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public List<GraphQLView.TransactionView> transactions(
            @Argument UUID accountId,
            @Argument GraphQLInput.TransactionFilter filter,
            @Argument GraphQLInput.TransactionSort sort,
            @Argument Integer limit,
            @Argument Integer offset) {
        return service.transactions(accountId, filter, sort, limit, offset);
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public GraphQLView.TransactionView transaction(@Argument UUID id) {
        return service.transaction(id);
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public List<GraphQLView.PaymentView> paymentCalendar(@Argument UUID companyId) {
        return service.payments(companyId);
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public List<GraphQLView.PaymentRequestView> paymentRequests() {
        return service.paymentRequests();
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public GraphQLView.PaymentRequestView paymentRequest(@Argument UUID id) {
        return service.paymentRequest(id);
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public List<GraphQLView.ForecastView> forecasts(@Argument UUID companyId) {
        return service.forecasts(companyId);
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public GraphQLView.AggregateView transactionAggregate(
            @Argument UUID companyId,
            @Argument LocalDate from,
            @Argument LocalDate to) {
        return service.aggregate(companyId, from, to);
    }

    

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN','FINANCIAL_MANAGER')")
    public GraphQLView.MutationResult createCompany(
            @Argument @Valid GraphQLInput.CompanyInput input,
            @Argument UUID ownerId) {
        return service.createCompany(input, ownerId);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN','FINANCIAL_MANAGER')")
    public GraphQLView.MutationResult updateCompany(
            @Argument @Valid GraphQLInput.CompanyInput input,
            @Argument UUID id) {
        return service.updateCompany(id, input);
    }

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public GraphQLView.MutationResult deleteCompany(@Argument UUID id) {
        return service.deleteCompany(id);
    }

    

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN','FINANCIAL_MANAGER')")
    public GraphQLView.MutationResult createAccount(
            @Argument @Valid GraphQLInput.AccountInput input) {
        return service.createAccount(input);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN','FINANCIAL_MANAGER')")
    public GraphQLView.MutationResult updateAccount(
            @Argument @Valid GraphQLInput.AccountInput input,
            @Argument UUID id) {
        return service.updateAccount(id, input);
    }

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public GraphQLView.MutationResult deleteAccount(@Argument UUID id) {
        return service.deleteAccount(id);
    }

    

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN','FINANCIAL_MANAGER')")
    public GraphQLView.MutationResult createTransaction(
            @Argument @Valid GraphQLInput.TransactionInput input) {
        return service.createTransaction(input);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN','FINANCIAL_MANAGER')")
    public GraphQLView.MutationResult updateTransaction(
            @Argument @Valid GraphQLInput.TransactionInput input,
            @Argument UUID id) {
        return service.updateTransaction(id, input);
    }

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public GraphQLView.MutationResult deleteTransaction(@Argument UUID id) {
        return service.deleteTransaction(id);
    }

    

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN','FINANCIAL_MANAGER')")
    public GraphQLView.MutationResult createPayment(
            @Argument @Valid GraphQLInput.PaymentInput input) {
        return service.createPayment(input);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN','FINANCIAL_MANAGER')")
    public GraphQLView.MutationResult createPaymentRequest(
            @Argument @Valid GraphQLInput.PaymentRequestInput input) {
        return service.createPaymentRequest(input);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN','FINANCIAL_MANAGER')")
    public GraphQLView.MutationResult updatePayment(
            @Argument @Valid GraphQLInput.PaymentInput input,
            @Argument UUID id) {
        return service.updatePayment(id, input);
    }

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public GraphQLView.MutationResult deletePayment(@Argument UUID id) {
        return service.deletePayment(id);
    }
}
