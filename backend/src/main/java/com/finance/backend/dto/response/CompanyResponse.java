package com.finance.backend.dto.response;

import lombok.Data;
import java.util.UUID;

@Data
public class CompanyResponse {
    private UUID id;
    private String name;
    private String taxId;
    private String address;
    private String phone;
    private String description;
    private String logoUrl;
    private String status;
    
    private UUID ownerId;
    private String ownerName;
}