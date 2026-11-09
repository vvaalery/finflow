package com.finance.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class TransactionRequest {

    @NotNull(message = "ID счета обязателен")
    private UUID accountId;

    @NotNull(message = "Тип транзакции обязателен")
    private String type;

    @NotNull(message = "Сумма обязательна")
    @Positive(message = "Сумма должна быть положительной")
    private BigDecimal amount;

    private String description;
    private String category;
    private String counterparty;
    private LocalDate date;

    private UUID cfoId;
    private UUID cashFlowItemId;
    private UUID counterpartyId;
}