package com.finance.graphqlapi.client;

import com.finance.graphqlapi.dto.GraphQLInput;
import com.finance.graphqlapi.dto.GraphQLView;
import com.finance.graphqlapi.service.GraphQLFinanceService;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class FinanceServiceClientMock implements FinanceServiceClient {
    private final GraphQLFinanceService service;
    public FinanceServiceClientMock(GraphQLFinanceService service){this.service=service;}
    public List<GraphQLView.UserView> users(){return service.users();} public GraphQLView.UserView user(UUID id){return service.user(id);}
    public List<GraphQLView.CompanyView> companies(){return service.companies();} public GraphQLView.CompanyView company(UUID id){return service.company(id);}
    public List<GraphQLView.AccountView> accounts(UUID id){return service.accounts(id);} public GraphQLView.AccountView account(UUID id){return service.account(id);}
    public List<GraphQLView.TransactionView> transactions(UUID id,GraphQLInput.TransactionFilter f,GraphQLInput.TransactionSort s,Integer l,Integer o){return service.transactions(id,f,s,l,o);} public GraphQLView.TransactionView transaction(UUID id){return service.transaction(id);}
    public List<GraphQLView.PaymentView> payments(UUID id){return service.payments(id);} public List<GraphQLView.PaymentRequestView> paymentRequests(){return service.paymentRequests();} public GraphQLView.PaymentRequestView paymentRequest(UUID id){return service.paymentRequest(id);}
    public List<GraphQLView.ForecastView> forecasts(UUID id){return service.forecasts(id);} public GraphQLView.AggregateView aggregate(UUID id,LocalDate f,LocalDate t){return service.aggregate(id,f,t);}
    public GraphQLView.MutationResult createCompany(GraphQLInput.CompanyInput i,UUID o){return service.createCompany(i,o);} public GraphQLView.MutationResult updateCompany(UUID id,GraphQLInput.CompanyInput i){return service.updateCompany(id,i);} public GraphQLView.MutationResult deleteCompany(UUID id){return service.deleteCompany(id);}
    public GraphQLView.MutationResult createAccount(GraphQLInput.AccountInput i){return service.createAccount(i);} public GraphQLView.MutationResult updateAccount(UUID id,GraphQLInput.AccountInput i){return service.updateAccount(id,i);} public GraphQLView.MutationResult deleteAccount(UUID id){return service.deleteAccount(id);}
    public GraphQLView.MutationResult createTransaction(GraphQLInput.TransactionInput i){return service.createTransaction(i);} public GraphQLView.MutationResult updateTransaction(UUID id,GraphQLInput.TransactionInput i){return service.updateTransaction(id,i);} public GraphQLView.MutationResult deleteTransaction(UUID id){return service.deleteTransaction(id);}
    public GraphQLView.MutationResult createPayment(GraphQLInput.PaymentInput i){return service.createPayment(i);} public GraphQLView.MutationResult createPaymentRequest(GraphQLInput.PaymentRequestInput i){return service.createPaymentRequest(i);} public GraphQLView.MutationResult updatePayment(UUID id,GraphQLInput.PaymentInput i){return service.updatePayment(id,i);} public GraphQLView.MutationResult deletePayment(UUID id){return service.deletePayment(id);}
}
