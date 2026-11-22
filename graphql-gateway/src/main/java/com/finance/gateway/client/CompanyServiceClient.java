package com.finance.gateway.client;

import com.finance.graphqlapi.dto.GraphQLInput.*;
import com.finance.graphqlapi.dto.GraphQLView.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CompanyServiceClient {
    List<CompanyView> companies();
    CompanyView company(UUID id);
    MutationResult createCompany(CompanyInput input, UUID ownerId);
    MutationResult updateCompany(UUID id, CompanyInput input);
    MutationResult deleteCompany(UUID id);

    List<AccountView> accounts(UUID companyId);
    AccountView account(UUID id);
    MutationResult createAccount(AccountInput input);
    MutationResult updateAccount(UUID id, AccountInput input);
    MutationResult deleteAccount(UUID id);

    List<TransactionView> transactions(TransactionQuery query);
    TransactionView transaction(UUID id);
    MutationResult createTransaction(TransactionInput input);
    MutationResult updateTransaction(UUID id, TransactionInput input);
    MutationResult deleteTransaction(UUID id);
    AggregateView aggregate(UUID companyId, LocalDate from, LocalDate to);
}
