package com.finance.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AccountRequest {
    @NotBlank(message = "Название счета обязательно")
    private String accountName;
    
    @NotBlank(message = "Тип счета обязателен")
    private String accountType;
    
    private String currency = "BYN";
}