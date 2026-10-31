package com.finance.graphqlapi.client;

import com.finance.graphqlapi.dto.GraphQLInput;
import com.finance.graphqlapi.dto.GraphQLView;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface FinanceServiceClient {
    List<GraphQLView.UserView> users(); GraphQLView.UserView user(UUID id);
    List<GraphQLView.CompanyView> companies(); GraphQLView.CompanyView company(UUID id);
    List<GraphQLView.AccountView> accounts(UUID companyId); GraphQLView.AccountView account(UUID id);
    List<GraphQLView.TransactionView> transactions(UUID accountId, GraphQLInput.TransactionFilter filter, GraphQLInput.TransactionSort sort, Integer limit, Integer offset);
    GraphQLView.TransactionView transaction(UUID id);
    List<GraphQLView.PaymentView> payments(UUID companyId); List<GraphQLView.PaymentRequestView> paymentRequests(); GraphQLView.PaymentRequestView paymentRequest(UUID id);
    List<GraphQLView.ForecastView> forecasts(UUID companyId); GraphQLView.AggregateView aggregate(UUID companyId, LocalDate from, LocalDate to);
    GraphQLView.MutationResult createCompany(GraphQLInput.CompanyInput input, UUID ownerId); GraphQLView.MutationResult updateCompany(UUID id, GraphQLInput.CompanyInput input); GraphQLView.MutationResult deleteCompany(UUID id);
    GraphQLView.MutationResult createAccount(GraphQLInput.AccountInput input); GraphQLView.MutationResult updateAccount(UUID id, GraphQLInput.AccountInput input); GraphQLView.MutationResult deleteAccount(UUID id);
    GraphQLView.MutationResult createTransaction(GraphQLInput.TransactionInput input); GraphQLView.MutationResult updateTransaction(UUID id, GraphQLInput.TransactionInput input); GraphQLView.MutationResult deleteTransaction(UUID id);
    GraphQLView.MutationResult createPayment(GraphQLInput.PaymentInput input); GraphQLView.MutationResult createPaymentRequest(GraphQLInput.PaymentRequestInput input); GraphQLView.MutationResult updatePayment(UUID id, GraphQLInput.PaymentInput input); GraphQLView.MutationResult deletePayment(UUID id);
}
