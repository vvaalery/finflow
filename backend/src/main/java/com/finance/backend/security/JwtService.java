package com.finance.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(
            secret.getBytes(StandardCharsets.UTF_8)
        );
    }

    public String generateToken(String email, String role) {
        long now = System.currentTimeMillis();

        return Jwts.builder()
            .id(UUID.randomUUID().toString())
            .claim("role", role)
            .subject(email)
            .issuedAt(new Date(now))
            .expiration(new Date(now + expiration))
            .signWith(getSigningKey())
            .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public String extractSubject(String token) {
        return parse(token).getSubject();
    }

    public String extractSessionId(String token) {
        return parse(token).getId();
    }

    public long getExpirationMillis() {
        return expiration;
    }

    public boolean isTokenValid(String token, String email) {
        Claims claims = parse(token);

        return email != null
            && email.equals(claims.getSubject())
            && claims.getId() != null
            && !claims.getId().isBlank()
            && claims.getExpiration() != null
            && claims.getExpiration().after(new Date());
    }
}
