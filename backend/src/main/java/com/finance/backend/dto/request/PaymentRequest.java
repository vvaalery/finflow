package com.finance.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class PaymentRequest {

    @NotNull(message = "ID компании обязателен")
    private UUID companyId;

    @NotBlank(message = "Название платежа обязательно")
    private String title;

    private String description;

    @NotNull(message = "Сумма обязательна")
    @Positive(message = "Сумма должна быть положительной")
    private BigDecimal amount;

    @NotNull(message = "Дата платежа обязательна")
    private LocalDate dueDate;

    private String category;
    private String counterparty;

    private UUID cashFlowItemId;
    private UUID counterpartyId;
}