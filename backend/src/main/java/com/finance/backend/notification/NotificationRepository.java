package com.finance.backend.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId);
    long countByUserIdAndSeenFalseAndDismissedFalse(UUID userId);
    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("update Notification n set n.seen = true, n.readAt = :now where n.userId = :userId and n.seen = false and n.dismissed = false")
    int markAllRead(@Param("userId") UUID userId, @Param("now") LocalDateTime now);
}
