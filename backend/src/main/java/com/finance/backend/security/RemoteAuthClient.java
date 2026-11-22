package com.finance.backend.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

@Component
public class RemoteAuthClient {
    private static final Logger log = LoggerFactory.getLogger(RemoteAuthClient.class);
    private final RestClient http;

    public RemoteAuthClient(RestClient.Builder builder, @Value("${services.auth-url}") String url) {
        this.http = builder.baseUrl(url).build();
    }

    public record RemoteUser(UUID id, String email, String fullName, RemoteRole role, boolean isActive) {}
    public record RemoteRole(Integer id, String name) {}

    public Optional<RemoteUser> validate(String token) {
        try {
            return Optional.ofNullable(http.get().uri("/internal/auth/validate")
                    .headers(h -> h.setBearerAuth(token)).retrieve().body(RemoteUser.class));
        } catch (RuntimeException e) {
            log.debug("Сессия не подтверждена Auth Service: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
