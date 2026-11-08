package com.finance.backend.notification;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notifications",
        uniqueConstraints = @UniqueConstraint(name = "uq_notifications_user_key", columnNames = {"user_id", "dedupe_key"}),
        indexes = @Index(name = "idx_notifications_user_created", columnList = "user_id, created_at"))
@Data
@NoArgsConstructor
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "dedupe_key", nullable = false, length = 200)
    private String dedupeKey;

    @Column(name = "type", nullable = false, length = 40)
    private String type;

    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "link_tab", length = 40)
    private String linkTab;

    @Column(name = "is_read", nullable = false)
    private boolean seen = false;

    @Column(name = "dismissed", nullable = false)
    private boolean dismissed = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
