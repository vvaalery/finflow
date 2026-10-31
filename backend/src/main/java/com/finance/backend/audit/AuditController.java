package com.finance.backend.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {
    private final AuditLogRepository repository;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR')")
    public List<AuditLog> getAuditLogs() { return repository.findTop100ByOrderByCreatedAtDesc(); }
}
