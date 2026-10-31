package com.finance.graphqlapi.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public final class GraphQLView {
    private GraphQLView() {}
    public record RoleView(Integer id, String name) {}
    public record UserView(UUID id, String email, String fullName, RoleView role, boolean isActive, LocalDateTime createdAt) {}
    public record CompanyView(UUID id, String name, String taxId, String address, String phone, String description, String websiteUrl, String logoUrl, String status, UUID ownerId, LocalDateTime createdAt) {}
    public record AccountView(UUID id, UUID companyId, String accountName, String accountType, String currency, BigDecimal balance, boolean isActive, LocalDateTime createdAt) {}
    public record TransactionView(UUID id, UUID accountId, String transactionType, BigDecimal amount, LocalDate date, String description, String category, String counterparty, UUID cfoId, LocalDateTime createdAt) {}
    public record PaymentView(UUID id, UUID companyId, String title, String description, BigDecimal amount, LocalDate dueDate, String status, String category, String counterparty, UUID createdBy, LocalDateTime createdAt) {}
    public record PaymentRequestView(UUID id, String title, BigDecimal amount, LocalDate date, String status, String requester, String counterparty, String category, String description) {}
    public record ForecastView(UUID id, UUID companyId, LocalDate start, LocalDate end, BigDecimal inflow, BigDecimal outflow, BigDecimal net, String strategy, LocalDateTime createdAt) {}
    public record AggregateView(BigDecimal income, BigDecimal expense, BigDecimal net, int transactionCount, BigDecimal averageAmount) {}
    public record MutationResult(boolean success, String message, String id) {}
}
