package com.example.common.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

/**
 * The caller's identity, taken entirely from verified token claims.
 *
 * <p>Downstream services never look the user up in a database — they cannot, since user-service owns
 * that table. The token is the source of truth for who is calling and what role they hold.
 */
public record AuthenticatedUser(Long userId, String username, String role) {

    public Collection<? extends GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
