package com.example.user.config;

import com.example.user.entity.Role;
import com.example.user.entity.User;
import com.example.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Creates the first ADMIN account, because self-registration only ever produces USER accounts and
 * the catalog would otherwise be unmanageable on a fresh database.
 *
 * <p>Enabled by {@code bookstore.admin.bootstrap-enabled}. In production, set the credentials
 * through environment variables and disable this once a real admin exists.
 */
@Configuration
@ConditionalOnProperty(name = "bookstore.admin.bootstrap-enabled", havingValue = "true")
public class AdminAccountInitializer {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    @Bean
    public ApplicationRunner adminAccountRunner(UserRepository userRepository,
                                                PasswordEncoder passwordEncoder,
                                                Environment environment) {
        return args -> {
            String username = environment.getProperty("bookstore.admin.username", "admin");
            String email = environment.getProperty("bookstore.admin.email", "admin@bookstore.local");
            String password = environment.getProperty("bookstore.admin.password");

            if (password == null || password.isBlank()) {
                log.warn("Admin bootstrap enabled but bookstore.admin.password is not set; skipping");
                return;
            }
            if (userRepository.existsByUsernameIgnoreCase(username)) {
                log.info("Admin account '{}' already exists; leaving it untouched", username);
                return;
            }

            userRepository.save(User.builder()
                    .username(username)
                    .email(email)
                    .passwordHash(passwordEncoder.encode(password))
                    .role(Role.ADMIN)
                    .build());

            log.info("Created bootstrap ADMIN account '{}'", username);
        };
    }
}
