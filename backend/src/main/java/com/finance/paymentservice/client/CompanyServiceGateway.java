package com.finance.paymentservice.client;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyServiceGateway {

    record CompanyDto(UUID id, String name, String taxId, String address, String phone, UUID ownerId,
                      String description, String websiteUrl, String logoUrl, String status) {}

    record AccountDto(UUID id, UUID companyId, String accountName, String accountType, String currency,
                      BigDecimal balance, boolean active) {}

    record ForecastInputDto(BigDecimal baseCurrencyBalance, List<AccountDto> accounts) {}

    record BalanceCheckDto(boolean sufficient, BigDecimal accountBalance, BigDecimal projectedBalance, String message) {}

    boolean accountBelongsToCompany(UUID accountId, UUID companyId);

    Optional<CompanyDto> getCompany(UUID id);

    Optional<AccountDto> getAccount(UUID id);

    CompanyDto saveCompany(CompanyDto company);

    void deleteCompany(UUID id);

    AccountDto saveAccount(AccountDto account);

    void deleteAccount(UUID id);

    BigDecimal changeBalance(UUID operationId, UUID accountId, BigDecimal delta);

    ForecastInputDto forecastInput(UUID companyId);

    BalanceCheckDto checkProjectedBalance(UUID companyId, UUID accountId, BigDecimal amount,
                                          BigDecimal committedOutflows, BigDecimal projectedNetFlow);
}
