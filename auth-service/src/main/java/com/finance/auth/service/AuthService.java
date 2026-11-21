package com.finance.auth.service;

import com.finance.auth.dto.AuthDto.*;
import com.finance.auth.entity.Role;
import com.finance.auth.entity.User;
import com.finance.auth.exception.ApiException;
import com.finance.auth.repository.RoleRepository;
import com.finance.auth.repository.UserRepository;
import com.finance.auth.security.JwtService;
import com.finance.auth.security.SessionService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
    public static final String DEFAULT_ROLE = "FINANCIAL_MANAGER";

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final SessionService sessions;

    @Transactional
    public LoginResult register(RegisterRequest r) {
        String email = normalize(r.email());
        if (users.existsByEmail(email)) throw new ApiException(409, "Пользователь с таким email уже зарегистрирован");
        checkPassword(r.password());
        Role role = roles.findByName(DEFAULT_ROLE).orElseThrow(() -> new ApiException(500, "Роль по умолчанию не настроена"));
        return open(users.save(newUser(email, r.fullName().trim(), r.password(), role)));
    }

    public LoginResult login(LoginRequest r) {
        User user = users.findByEmail(normalize(r.email())).orElse(null);
        if (user == null || !encoder.matches(r.password(), user.getPasswordHash()))
            throw new ApiException(401, "Неверный email или пароль");
        if (!Boolean.TRUE.equals(user.getIsActive())) throw new ApiException(403, "Учётная запись отключена");
        return open(user);
    }

    public UserDto authenticate(String authorizationHeader) {
        String token = bearer(authorizationHeader);
        Claims claims;
        UUID userId;
        try {
            claims = jwt.parse(token);
            userId = UUID.fromString(claims.get("uid", String.class));
        } catch (RuntimeException e) {
            throw new ApiException(401, "Недействительный токен");
        }
        if (!sessions.isActive(claims.getId(), userId)) throw new ApiException(401, "Сессия завершена или истекла");
        User user = users.findById(userId).orElseThrow(() -> new ApiException(401, "Пользователь не найден"));
        if (!Boolean.TRUE.equals(user.getIsActive())) throw new ApiException(403, "Учётная запись отключена");
        return toDto(user);
    }

    public void logout(String authorizationHeader) {
        try {
            sessions.revoke(jwt.parse(bearer(authorizationHeader)).getId());
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException ignored) {
        }
    }

    public List<UserDto> list() { return users.findAll().stream().map(this::toDto).toList(); }

    public List<RoleDto> roleList() { return roles.findAll().stream().map(r -> new RoleDto(r.getId(), r.getName())).toList(); }

    public UserDto byId(UUID id) {
        return users.findById(id).map(this::toDto).orElseThrow(() -> new ApiException(404, "Пользователь не найден"));
    }

    public UserDto byEmail(String email) {
        return users.findByEmail(normalize(email)).map(this::toDto).orElseThrow(() -> new ApiException(404, "Пользователь не найден"));
    }

    @Transactional
    public UserDto createUser(AdminCreateUser r) {
        String email = normalize(r.email());
        if (users.existsByEmail(email)) throw new ApiException(409, "Пользователь с таким email уже существует");
        checkPassword(r.password());
        Role role = roles.findById(r.roleId()).orElseThrow(() -> new ApiException(400, "Роль не найдена"));
        return toDto(users.save(newUser(email, r.fullName().trim(), r.password(), role)));
    }

    @Transactional
    public UserDto setActive(UUID id, boolean active) {
        User user = users.findById(id).orElseThrow(() -> new ApiException(404, "Пользователь не найден"));
        user.setIsActive(active);
        return toDto(users.save(user));
    }

    @Transactional
    public UserDto updateFullName(String email, String fullName) {
        User user = users.findByEmail(normalize(email)).orElseThrow(() -> new ApiException(404, "Пользователь не найден"));
        if (fullName != null && !fullName.isBlank()) user.setFullName(fullName.trim());
        return toDto(users.save(user));
    }

    private LoginResult open(User user) {
        JwtService.Issued issued = jwt.issue(user);
        sessions.create(issued.jti(), user.getId(), jwt.ttl());
        return new LoginResult(issued.token(), toDto(user));
    }

    private User newUser(String email, String fullName, String password, Role role) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(email);
        u.setFullName(fullName);
        u.setPasswordHash(encoder.encode(password));
        u.setRole(role);
        u.setIsActive(true);
        return u;
    }

    private void checkPassword(String p) {
        if (p == null || p.length() < 8 || p.length() > 72 || !p.matches(".*\\p{L}.*") || !p.matches(".*\\d.*"))
            throw new ApiException(400, "Пароль: от 8 до 72 символов, минимум одна буква и одна цифра");
    }

    private String bearer(String header) {
        if (header == null || !header.startsWith("Bearer ") || header.length() < 8)
            throw new ApiException(401, "Требуется аутентификация");
        return header.substring(7);
    }

    private String normalize(String email) { return email == null ? "" : email.trim().toLowerCase(Locale.ROOT); }

    public UserDto toDto(User u) {
        return new UserDto(u.getId(), u.getEmail(), u.getFullName(), new RoleDto(u.getRole().getId(), u.getRole().getName()),
                Boolean.TRUE.equals(u.getIsActive()), u.getCreatedAt(), u.getUpdatedAt());
    }
}
