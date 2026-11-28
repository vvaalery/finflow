package com.finance.gateway.security;

import com.finance.gateway.client.AuthServiceClient;
import com.finance.gateway.logging.Correlation;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(SessionAuthenticationFilter.class);
    private final AuthServiceClient auth;

    public SessionAuthenticationFilter(AuthServiceClient auth) {
        this.auth = auth;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"/graphql".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && header.length() > 7) {
            String token = header.substring(7);
            try {
                auth.validate(token).filter(u -> u.isActive() && u.role() != null).ifPresent(user -> {
                    var principal = new AuthenticatedUser(user, token);
                    var authentication = new UsernamePasswordAuthenticationToken(
                            principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
                    authentication.setDetails(org.slf4j.MDC.get(Correlation.MDC_KEY));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
            } catch (RuntimeException e) {
                log.warn("Не удалось проверить сессию в Auth Service: {}", e.getMessage());
            }
        }
        chain.doFilter(request, response);
    }
}
