package com.finance.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.UUID;

@Data
@AllArgsConstructor
public class AuthResponse {
    private UUID id; 
    private String token;
    private String role;
    private String fullName;
    private String email;
}