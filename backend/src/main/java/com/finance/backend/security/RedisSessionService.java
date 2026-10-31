package com.finance.backend.security;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisSessionService {

    private static final String SESSION_PREFIX = "auth:session:";

    private final StringRedisTemplate redisTemplate;

    public RedisSessionService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void saveSession(String sessionId, String email, Duration ttl) {
        if (sessionId == null || sessionId.isBlank()
                || email == null || email.isBlank()
                || ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Некорректные параметры сессии");
        }

        redisTemplate.opsForValue().set(
            SESSION_PREFIX + sessionId,
            email,
            ttl
        );
    }

    public boolean isSessionActive(String sessionId, String email) {
        if (sessionId == null || sessionId.isBlank() || email == null) {
            return false;
        }

        String savedEmail = redisTemplate.opsForValue().get(
            SESSION_PREFIX + sessionId
        );

        return email.equals(savedEmail);
    }

    public void revokeSession(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            redisTemplate.delete(SESSION_PREFIX + sessionId);
        }
    }
}
