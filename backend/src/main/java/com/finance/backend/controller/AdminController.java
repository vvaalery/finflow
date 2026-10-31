package com.finance.backend.controller;

import com.finance.backend.entity.Role;
import com.finance.backend.entity.User;
import com.finance.backend.repository.RoleRepository;
import com.finance.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/users")
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/roles")
    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/users")
    public User createUser(@RequestBody Map<String, Object> payload) {
        String email = (String) payload.get("email");
        String password = (String) payload.get("password");
        String fullName = (String) payload.get("fullName");
        Integer roleId = payload.get("roleId") instanceof Number n ? n.intValue() : null;
        if (email == null || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите уникальный корректный email");
        }
        if (password == null || password.length() < 8 || fullName == null || fullName.isBlank() || roleId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Имя, роль и пароль не короче 8 символов обязательны");
        }

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Роль не найдена"));

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFullName(fullName);
        user.setRole(role);
        user.setIsActive(true);

        return userRepository.save(user);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/users/{id}/status")
    public User toggleUserStatus(@PathVariable UUID id, @RequestBody Map<String, Boolean> payload) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
        user.setIsActive(payload.get("isActive"));
        return userRepository.save(user);
    }
}