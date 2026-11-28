package com.finance.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Component
public class ServiceTokenFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Internal-Token";
    public static final String ROLE = "ROLE_SERVICE";

    private final byte[] token;

    public ServiceTokenFilter(@Value("${internal.service-token:}") String token) {
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    public static boolean isService() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getAuthorities().stream()
                .anyMatch(x -> ROLE.equals(x.getAuthority()));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().endsWith("/cash-flow-history");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        String provided = request.getHeader(HEADER);

        if (token.length > 0 && provided != null
                && MessageDigest.isEqual(token, provided.getBytes(StandardCharsets.UTF_8))
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            "forecast-service",
                            null,
                            List.of(new SimpleGrantedAuthority(ROLE))
                    )
            );
        }

        chain.doFilter(request, response);
    }
}
