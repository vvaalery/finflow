package com.finance.auth.config;

import com.finance.auth.entity.Role;
import com.finance.auth.entity.User;
import com.finance.auth.repository.RoleRepository;
import com.finance.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class DataSeeder implements ApplicationRunner {
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final boolean seedDemoUsers;

    public DataSeeder(UserRepository users, RoleRepository roles, PasswordEncoder encoder,
                      @Value("${auth.seed-demo-users:true}") boolean seedDemoUsers) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.seedDemoUsers = seedDemoUsers;
    }

    public static UUID idFor(String email) {
        return UUID.nameUUIDFromBytes(("finflow-user:" + email).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Role admin = role("ADMIN");
        Role manager = role("FINANCIAL_MANAGER");
        Role director = role("DIRECTOR");
        if (!seedDemoUsers) return;
        demo("admin@finflow.local", "System Administrator", "Admin123!", admin);
        demo("manager@finflow.local", "Финансовый менеджер", "Manager123!", manager);
        demo("director@finflow.local", "Директор компании", "Director123!", director);
    }

    private Role role(String name) {
        return roles.findByName(name).orElseGet(() -> {
            Role r = new Role();
            r.setName(name);
            return roles.save(r);
        });
    }

    private void demo(String email, String fullName, String password, Role role) {
        if (users.existsByEmail(email)) return;
        User u = new User();
        u.setId(idFor(email));
        u.setEmail(email);
        u.setFullName(fullName);
        u.setPasswordHash(encoder.encode(password));
        u.setRole(role);
        u.setIsActive(true);
        users.save(u);
    }
}
