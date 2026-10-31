package com.finance.backend.audit;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.net.InetAddress;
import java.util.UUID;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_logs_created_at", columnList = "created_at"),
        @Index(name = "idx_audit_logs_entity", columnList = "entity_type,entity_id"),
        @Index(name = "idx_audit_logs_user", columnList = "user_id")
})
@Data
@NoArgsConstructor
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "user_id") private UUID userId;
    @Column(nullable = false) private String action;
    @Column(name = "entity_type") private String entityType;
    @Column(name = "entity_id") private String entityId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "old_value", columnDefinition = "jsonb") private JsonNode oldValue;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "new_value", columnDefinition = "jsonb") private JsonNode newValue;
    @Column(name = "ip_address", columnDefinition = "inet") private InetAddress ipAddress;
    @Column(name = "user_agent") private String userAgent;
    @CreationTimestamp @Column(name = "created_at") private LocalDateTime createdAt;
}
