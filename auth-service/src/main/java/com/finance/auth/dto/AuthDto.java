package com.finance.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

public final class AuthDto {
    private AuthDto() {}

    public record RoleDto(Integer id, String name) {}
    public record UserDto(UUID id, String email, String fullName, RoleDto role, boolean isActive,
                          LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
    public record RegisterRequest(@Email @NotBlank String email,
                                  @NotBlank @Size(min = 8, max = 72) String password,
                                  @NotBlank @Size(max = 255) String fullName) {}
    public record AdminCreateUser(@Email @NotBlank String email, @NotBlank String password,
                                  @NotBlank String fullName, @NotNull Integer roleId) {}
    public record ProfileUpdate(String email, String fullName) {}
    public record LoginResult(String token, UserDto user) {}
    public record AuthResponse(UUID id, String token, String role, String fullName, String email) {}
}
