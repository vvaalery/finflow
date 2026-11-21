package com.finance.auth.controller;

import com.finance.auth.dto.AuthDto.*;
import com.finance.auth.exception.ApiException;
import com.finance.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AuthController {
    private static final String AUTH = "Authorization";
    private final AuthService auth;

    @PostMapping("/api/auth/login")
    public AuthResponse legacyLogin(@Valid @RequestBody LoginRequest r) {
        LoginResult res = auth.login(r);
        return new AuthResponse(res.user().id(), res.token(), res.user().role().name(), res.user().fullName(), res.user().email());
    }

    @GetMapping("/api/admin/users")
    public List<UserDto> adminUsers(@RequestHeader(value = AUTH, required = false) String h) {
        admin(h);
        return auth.list();
    }

    @GetMapping("/api/admin/roles")
    public List<RoleDto> adminRoles(@RequestHeader(value = AUTH, required = false) String h) {
        admin(h);
        return auth.roleList();
    }

    @PostMapping("/api/admin/users")
    public UserDto adminCreate(@RequestHeader(value = AUTH, required = false) String h, @Valid @RequestBody AdminCreateUser body) {
        admin(h);
        return auth.createUser(body);
    }

    @PatchMapping("/api/admin/users/{id}/status")
    public UserDto adminStatus(@RequestHeader(value = AUTH, required = false) String h, @PathVariable UUID id,
                               @RequestBody Map<String, Boolean> payload) {
        UserDto caller = admin(h);
        Boolean active = payload.get("isActive");
        if (active == null) throw new ApiException(400, "Поле isActive обязательно");
        if (!active && caller.id().equals(id)) throw new ApiException(400, "Нельзя отключить собственную учётную запись");
        return auth.setActive(id, active);
    }

    @GetMapping("/api/users/profile")
    public UserDto profile(@RequestHeader(value = AUTH, required = false) String h, @RequestParam String email) {
        UserDto caller = auth.authenticate(h);
        selfOrAdmin(caller, email);
        return auth.byEmail(email);
    }

    @PutMapping("/api/users/profile")
    public UserDto updateProfile(@RequestHeader(value = AUTH, required = false) String h, @RequestBody ProfileUpdate body) {
        UserDto caller = auth.authenticate(h);
        selfOrAdmin(caller, body.email());
        return auth.updateFullName(body.email(), body.fullName());
    }

    @PostMapping("/internal/auth/login")
    public LoginResult login(@Valid @RequestBody LoginRequest r) { return auth.login(r); }

    @PostMapping("/internal/auth/register")
    public LoginResult register(@Valid @RequestBody RegisterRequest r) { return auth.register(r); }

    @PostMapping("/internal/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = AUTH, required = false) String h) { auth.logout(h); }

    @GetMapping("/internal/auth/validate")
    public UserDto validate(@RequestHeader(value = AUTH, required = false) String h) { return auth.authenticate(h); }

    @GetMapping("/internal/auth/users")
    public List<UserDto> users(@RequestHeader(value = AUTH, required = false) String h) {
        admin(h);
        return auth.list();
    }

    @GetMapping("/internal/auth/users/{id}")
    public UserDto user(@RequestHeader(value = AUTH, required = false) String h, @PathVariable UUID id) {
        UserDto caller = auth.authenticate(h);
        if (!"ADMIN".equals(caller.role().name()) && !caller.id().equals(id)) throw new ApiException(403, "Недостаточно прав");
        return auth.byId(id);
    }

    private UserDto admin(String h) {
        UserDto caller = auth.authenticate(h);
        if (!"ADMIN".equals(caller.role().name())) throw new ApiException(403, "Недостаточно прав");
        return caller;
    }

    private void selfOrAdmin(UserDto caller, String email) {
        if (!"ADMIN".equals(caller.role().name()) && (email == null || !caller.email().equalsIgnoreCase(email.trim())))
            throw new ApiException(403, "Недостаточно прав");
    }
}
