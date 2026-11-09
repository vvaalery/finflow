package com.finance.backend.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class TransactionResponse {

    private UUID id;
    private UUID accountId;
    private String accountName;
    private String transactionType;
    private BigDecimal amount;
    private LocalDate date;
    private String description;
    private String category;
    private String counterparty;
    private String cfoId;
    private UUID cashFlowItemId;
    private UUID counterpartyId;
}