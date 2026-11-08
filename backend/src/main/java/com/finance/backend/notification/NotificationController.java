package com.finance.backend.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@PreAuthorize("hasAnyRole('ADMIN','FINANCIAL_MANAGER','DIRECTOR')")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService service;

    @GetMapping
    public NotificationView.Page list(Authentication auth,
                                      @RequestParam(defaultValue = "false") boolean unreadOnly,
                                      @RequestParam(defaultValue = "false") boolean refresh) {
        var user = service.userByEmail(auth.getName());
        service.syncIfNeeded(user, refresh);
        return service.page(user.getId(), unreadOnly);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(Authentication auth) {
        var user = service.userByEmail(auth.getName());
        service.syncIfNeeded(user, false);
        return Map.of("count", service.unreadCount(user.getId()));
    }

    @PostMapping("/{id}/read")
    public Map<String, Boolean> markRead(Authentication auth, @PathVariable UUID id, @RequestParam(defaultValue = "true") boolean read) {
        service.markRead(service.userByEmail(auth.getName()).getId(), id, read);
        return Map.of("ok", true);
    }

    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead(Authentication auth) {
        return Map.of("updated", service.markAllRead(service.userByEmail(auth.getName()).getId()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable UUID id) {
        service.delete(service.userByEmail(auth.getName()).getId(), id);
    }
}
