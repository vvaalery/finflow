package com.finance.backend.controller;

import com.finance.backend.entity.User;
import com.finance.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserRepository userRepository;

    @GetMapping("/profile")
    public Map<String, Object> getProfile(Authentication authentication) {
        User user = currentUser(authentication);
        return profileView(user);
    }

    @PutMapping("/profile")
    public Map<String, Object> updateProfile(@RequestBody Map<String, String> payload, Authentication authentication) {
        User user = currentUser(authentication);
        String fullName = payload.get("fullName");
        if (fullName == null || fullName.isBlank() || fullName.length() > 160) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Имя обязательно и не должно превышать 160 символов");
        }
        user.setFullName(fullName.trim());
        return profileView(userRepository.save(user));
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Требуется авторизация");
        }
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));
    }

    private Map<String, Object> profileView(User user) {
        return Map.of("id", user.getId(), "email", user.getEmail(), "fullName", user.getFullName(),
                "role", user.getRole().getName(), "isActive", Boolean.TRUE.equals(user.getIsActive()));
    }
}
