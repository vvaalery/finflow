package com.finance.graphqlapi.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class GraphQLInput {
    private GraphQLInput() {}

    public record CompanyInput(@NotBlank String name, String taxId, String address, String phone, String description) {}
    public record AccountInput(@NotNull UUID companyId, @NotBlank String accountName, @NotBlank String accountType,
                               @NotBlank String currency, @NotNull @DecimalMin("0.0") BigDecimal balance) {}
    public record TransactionInput(@NotNull UUID accountId, @NotBlank String transactionType,
                                   @NotNull @DecimalMin("0.01") BigDecimal amount, @NotNull LocalDate date,
                                   String description, String category, String counterparty, UUID cfoId) {}
    public record PaymentInput(@NotNull UUID companyId, @NotBlank String title, @NotNull @DecimalMin("0.01") BigDecimal amount,
                               @NotNull LocalDate dueDate, String status, String category, String counterparty, String description) {}
    public record PaymentRequestInput(@NotNull UUID accountId, @NotNull UUID companyId, @NotBlank String title, @NotNull @DecimalMin("0.01") BigDecimal amount,
                                      @NotNull LocalDate date, String requester, String counterparty, String category, String description) {}
    public record TransactionFilter(String type, String category, LocalDate from, LocalDate to, BigDecimal minAmount, BigDecimal maxAmount) {}
    public record TransactionSort(String field, String direction) {}
}
