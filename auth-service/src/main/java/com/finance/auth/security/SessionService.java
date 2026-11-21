package com.finance.auth.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class SessionService {
    private static final String PREFIX = "finflow:auth:session:";
    private final StringRedisTemplate redis;

    public SessionService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void create(String jti, UUID userId, Duration ttl) {
        redis.opsForValue().set(PREFIX + jti, userId.toString(), ttl);
    }

    public boolean isActive(String jti, UUID userId) {
        return jti != null && userId.toString().equals(redis.opsForValue().get(PREFIX + jti));
    }

    public void revoke(String jti) {
        if (jti != null) redis.delete(PREFIX + jti);
    }
}
