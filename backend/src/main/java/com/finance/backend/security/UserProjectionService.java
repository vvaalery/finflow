package com.finance.backend.security;

import com.finance.backend.entity.Role;
import com.finance.backend.entity.User;
import com.finance.backend.repository.RoleRepository;
import com.finance.backend.repository.UserRepository;
import com.finance.kafka.event.EventTypes;
import com.finance.kafka.outbox.OutboxEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserProjectionService {
    public static final String EXTERNAL_HASH = "EXTERNAL_AUTH";

    private final UserRepository users;
    private final RoleRepository roles;
    private final OutboxEventService outbox;

    @Transactional
    public User sync(RemoteAuthClient.RemoteUser remote) {
        String roleName = remote.role().name();
        Role role = roles.findByName(roleName).orElseGet(() -> {
            Role r = new Role();
            r.setName(roleName);
            return roles.save(r);
        });

        User user = users.findByEmail(remote.email()).orElse(null);
        boolean created = false;

        if (user == null) {
            user = new User();
            user.setId(remote.id());
            user.setEmail(remote.email());
            user.setPasswordHash(EXTERNAL_HASH);
            created = true;
        } else if (remote.fullName().equals(user.getFullName()) && user.getRole() != null
                && roleName.equals(user.getRole().getName()) && Boolean.TRUE.equals(user.getIsActive())) {
            return user;
        }

        user.setFullName(remote.fullName());
        user.setRole(role);
        user.setIsActive(remote.isActive());

        User saved = users.save(user);

        if (created) {
            outbox.enqueue(EventTypes.USER_CREATED, saved.getId(), null,
                    "userId", saved.getId(), "email", saved.getEmail(), "fullName", saved.getFullName(),
                    "role", roleName);
        }

        return saved;
    }
}
