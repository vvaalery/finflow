package com.finance.backend.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class PaymentResponse {

    private UUID id;
    private String title;
    private String description;
    private BigDecimal amount;
    private LocalDate dueDate;
    private String status;
    private String category;
    private String counterparty;
    private String companyName;
    private String createdByName;
    private boolean isOverdue;
    private UUID cashFlowItemId;
    private UUID counterpartyId;
}