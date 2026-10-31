package com.finance.backend.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import com.finance.backend.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.net.InetAddress;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditLogRepository repository;
    private final ObjectMapper mapper;
    private final UserRepository userRepository;

    @Transactional
    public void log(String action, String entityType, String entityId, Object oldValue, Object newValue, HttpServletRequest request) {
        AuditLog log = new AuditLog();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails details) {
            userRepository.findByEmail(details.getUsername()).ifPresent(u -> log.setUserId(u.getId()));
        }
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setOldValue(toJson(oldValue));
        log.setNewValue(toJson(newValue));
        log.setIpAddress(resolveIp(request));
        log.setUserAgent(request == null ? null : request.getHeader("User-Agent"));
        repository.save(log);
    }

    private JsonNode toJson(Object value) {
        return value == null ? null : mapper.valueToTree(value);
    }

    private InetAddress resolveIp(HttpServletRequest request) {
        if (request == null) return null;
        String forwarded = request.getHeader("X-Forwarded-For");
        try { return InetAddress.getByName(forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim()); } catch (Exception e) { return null; }
    }
}
