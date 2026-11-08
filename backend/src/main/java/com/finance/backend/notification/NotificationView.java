package com.finance.backend.notification;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

public final class NotificationView {
    private NotificationView() {}

    public record Item(UUID id, String type, String severity, String title, String message, String linkTab,
                       boolean read, Instant createdAt) {
        static Item of(Notification n) {
            return new Item(n.getId(), n.getType(), n.getSeverity(), n.getTitle(), n.getMessage(), n.getLinkTab(), n.isSeen(),
                    n.getCreatedAt() == null ? null : n.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant());
        }
    }

    public record Page(long unreadCount, List<Item> items) {}
}
