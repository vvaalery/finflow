package com.finance.backend.dto.response;

import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data
public class AccountResponse {
    private UUID id;
    private String accountName;
    private String accountType;
    private String currency;
    private BigDecimal balance;
    private UUID companyId;
    private String companyName;
}