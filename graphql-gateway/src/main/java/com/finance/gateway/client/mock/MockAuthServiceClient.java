package com.finance.gateway.client.mock;

import com.finance.gateway.client.AuthServiceClient;
import com.finance.graphqlapi.dto.GraphQLView.AuthPayload;
import com.finance.graphqlapi.dto.GraphQLView.RoleView;
import com.finance.graphqlapi.dto.GraphQLView.UserView;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("mock")
public class MockAuthServiceClient implements AuthServiceClient {
    private final Map<String, UserView> byEmail = new ConcurrentHashMap<>();
    private final Map<String, String> passwords = new ConcurrentHashMap<>();
    private final Map<String, UserView> sessions = new ConcurrentHashMap<>();

    public MockAuthServiceClient() {
        add("admin@finflow.local", "System Administrator", "Admin123!", 3, "ADMIN");
        add("manager@finflow.local", "Финансовый менеджер", "Manager123!", 1, "FINANCIAL_MANAGER");
        add("director@finflow.local", "Директор компании", "Director123!", 2, "DIRECTOR");
    }

    public static UUID idFor(String email) {
        return UUID.nameUUIDFromBytes(("finflow-user:" + email).getBytes(StandardCharsets.UTF_8));
    }

    private void add(String email, String name, String password, int roleId, String role) {
        byEmail.put(email, new UserView(idFor(email), email, name, new RoleView(roleId, role), true, LocalDateTime.now()));
        passwords.put(email, password);
    }

    @Override
    public AuthPayload login(String email, String password) {
        UserView user = byEmail.get(email == null ? "" : email.trim().toLowerCase());
        if (user == null || !passwords.get(user.email()).equals(password)) {
            throw new BadCredentialsException("Неверный email или пароль");
        }
        return open(user);
    }

    @Override
    public AuthPayload register(String email, String password, String fullName) {
        String key = email.trim().toLowerCase();
        if (byEmail.containsKey(key)) {
            throw new IllegalArgumentException("Пользователь с таким email уже зарегистрирован");
        }
        add(key, fullName, password, 1, "FINANCIAL_MANAGER");
        return open(byEmail.get(key));
    }

    @Override
    public void logout(String token) {
        sessions.remove(token);
    }

    @Override
    public Optional<UserView> validate(String token) {
        return Optional.ofNullable(sessions.get(token));
    }

    @Override
    public List<UserView> users(String token) {
        return List.copyOf(byEmail.values());
    }

    @Override
    public UserView user(String token, UUID id) {
        return byEmail.values().stream()
                .filter(u -> u.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new java.util.NoSuchElementException("Пользователь не найден"));
    }

    private AuthPayload open(UserView user) {
        String token = "mock-" + UUID.randomUUID();
        sessions.put(token, user);
        return new AuthPayload(token, user);
    }
}
