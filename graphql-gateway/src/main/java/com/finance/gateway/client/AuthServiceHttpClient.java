package com.finance.gateway.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.graphqlapi.dto.GraphQLView.AuthPayload;
import com.finance.graphqlapi.dto.GraphQLView.UserView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class AuthServiceHttpClient implements AuthServiceClient {
    private final RestClient http;
    private final ObjectMapper mapper;

    public AuthServiceHttpClient(RestClient.Builder builder, ObjectMapper mapper, @Value("${services.auth-url}") String url) {
        this.http = builder.baseUrl(url).build();
        this.mapper = mapper;
    }

    @Override
    public AuthPayload login(String email, String password) {
        return ServiceCall.run(mapper, () -> http.post().uri("/internal/auth/login")
                .body(Map.of("email", email, "password", password)).retrieve().body(AuthPayload.class));
    }

    @Override
    public AuthPayload register(String email, String password, String fullName) {
        return ServiceCall.run(mapper, () -> http.post().uri("/internal/auth/register")
                .body(Map.of("email", email, "password", password, "fullName", fullName)).retrieve().body(AuthPayload.class));
    }

    @Override
    public void logout(String token) {
        ServiceCall.run(mapper, () -> {
            http.post().uri("/internal/auth/logout").headers(h -> h.setBearerAuth(token)).retrieve().toBodilessEntity();
            return null;
        });
    }

    @Override
    public Optional<UserView> validate(String token) {
        try {
            return Optional.ofNullable(ServiceCall.run(mapper, () -> http.get().uri("/internal/auth/validate")
                    .headers(h -> h.setBearerAuth(token)).retrieve().body(UserView.class)));
        } catch (BadCredentialsException | AccessDeniedException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<UserView> users(String token) {
        return ServiceCall.run(mapper, () -> http.get().uri("/internal/auth/users").headers(h -> h.setBearerAuth(token))
                .retrieve().body(new ParameterizedTypeReference<List<UserView>>() {}));
    }

    @Override
    public UserView user(String token, UUID id) {
        return ServiceCall.run(mapper, () -> http.get().uri("/internal/auth/users/{id}", id)
                .headers(h -> h.setBearerAuth(token)).retrieve().body(UserView.class));
    }
}
