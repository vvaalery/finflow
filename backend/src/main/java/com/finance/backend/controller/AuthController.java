package com.finance.backend.controller;

import com.finance.backend.dto.request.AuthRequest;
import com.finance.backend.dto.response.AuthResponse;
import com.finance.backend.entity.User;
import com.finance.backend.repository.UserRepository;
import com.finance.backend.security.JwtService;
import com.finance.backend.security.RedisSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RedisSessionService redisSessionService;

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody AuthRequest request) {
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                request.getEmail(),
                request.getPassword()
            )
        );

        User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        String token = jwtService.generateToken(
            user.getEmail(),
            user.getRole().getName()
        );

        String sessionId = jwtService.extractSessionId(token);

        redisSessionService.saveSession(
            sessionId,
            user.getEmail(),
            Duration.ofMillis(jwtService.getExpirationMillis())
        );

        return new AuthResponse(
            user.getId(),
            token,
            user.getRole().getName(),
            user.getFullName(),
            user.getEmail()
        );
    }
}
