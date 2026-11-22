package com.finance.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final RemoteAuthClient auth;
    private final UserProjectionService projection;

    public JwtAuthenticationFilter(RemoteAuthClient auth, UserProjectionService projection) {
        this.auth = auth;
        this.projection = projection;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && header.length() > 7
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                auth.validate(header.substring(7)).filter(RemoteAuthClient.RemoteUser::isActive).ifPresent(remote -> {
                    projection.sync(remote);
                    UserDetails details = org.springframework.security.core.userdetails.User.withUsername(remote.email())
                            .password("{noop}")
                            .roles(remote.role().name())
                            .build();
                    UsernamePasswordAuthenticationToken token =
                            new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
                    token.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(token);
                });
            } catch (RuntimeException e) {
                log.warn("Не удалось аутентифицировать запрос: {}", e.getMessage());
            }
        }
        chain.doFilter(request, response);
    }
}