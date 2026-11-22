package com.finance.internal.security;

import com.finance.backend.entity.Company;
import com.finance.backend.entity.User;
import com.finance.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class AccessPolicy {
    public static final String ADMIN = "ADMIN", MANAGER = "FINANCIAL_MANAGER", DIRECTOR = "DIRECTOR";

    private final UserRepository users;

    public User currentUser() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || a instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Требуется аутентификация");
        }
        return users.findByEmail(a.getName()).orElseThrow(() -> new AccessDeniedException("Пользователь не найден"));
    }

    public boolean isAdmin(User u) { return ADMIN.equals(roleOf(u)); }
    public boolean isDirector(User u) { return DIRECTOR.equals(roleOf(u)); }
    public boolean isManager(User u) { return MANAGER.equals(roleOf(u)); }
    public boolean canReadAll(User u) { return isAdmin(u) || isDirector(u); }

    public void requireRole(User u, String... allowed) {
        if (!Arrays.asList(allowed).contains(roleOf(u))) {
            throw new AccessDeniedException("Недостаточно прав для выполнения операции");
        }
    }

    public void requireReadCompany(User u, Company c) {
        if (!(canReadAll(u) || (isManager(u) && owns(u, c)))) {
            throw new AccessDeniedException("Нет доступа к данным этой компании");
        }
    }

    public void requireWriteCompany(User u, Company c) {
        if (!(isAdmin(u) || (isManager(u) && owns(u, c)))) {
            throw new AccessDeniedException("Нет прав на изменение данных этой компании");
        }
    }

    public boolean owns(User u, Company c) {
        return c.getOwner() != null && u.getId().equals(c.getOwner().getId());
    }

    private String roleOf(User u) {
        return u.getRole() == null ? "" : u.getRole().getName();
    }
}
