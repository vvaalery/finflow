package com.finance.auth.security;

import com.finance.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    private final SecretKey key;
    private final long expirationMs;

    public JwtService(@Value("${jwt.secret}") String secret, @Value("${jwt.expiration}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public record Issued(String token, String jti) {}

    public Issued issue(User user) {
        String jti = UUID.randomUUID().toString();
        Date now = new Date();
        String token = Jwts.builder()
                .id(jti)
                .subject(user.getEmail())
                .claim("uid", user.getId().toString())
                .claim("role", user.getRole().getName())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
        return new Issued(token, jti);
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public Duration ttl() {
        return Duration.ofMillis(expirationMs);
    }
}
