package com.finance.backend.controller;

import com.finance.backend.entity.User;
import com.finance.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @GetMapping("/profile")
    public User getProfile(@RequestParam String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
    }

    @PutMapping("/profile")
    public User updateProfile(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        String fullName = payload.get("fullName");
        
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
        
        if (fullName != null) {
            user.setFullName(fullName);
        }
        
        return userRepository.save(user);
    }
}