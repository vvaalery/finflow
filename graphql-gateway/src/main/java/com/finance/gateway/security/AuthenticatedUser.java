package com.finance.gateway.security;

import com.finance.graphqlapi.dto.GraphQLView.UserView;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public record AuthenticatedUser(UserView user, String token) {
    public UUID id() { return user.id(); }
    public String role() { return user.role().name(); }

    public static AuthenticatedUser current() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof AuthenticatedUser u) return u;
        throw new AccessDeniedException("Требуется аутентификация");
    }
}
