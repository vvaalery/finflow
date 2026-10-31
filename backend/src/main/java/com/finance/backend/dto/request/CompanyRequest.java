package com.finance.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CompanyRequest {
    @NotBlank(message = "Название компании обязательно")
    private String name;
    private String taxId;
    private String address;
    private String phone;
    private String description;
    private String logoUrl;
    private String status;
}