package com.example.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * The JWT contract every service shares. user-service issues tokens; the others only verify them,
 * so the same secret and issuer must be configured platform-wide.
 *
 * @param secret     HMAC-SHA signing key; must be at least 32 bytes
 * @param expiration how long an issued token stays valid (only user-service issues)
 * @param issuer     the {@code iss} claim, checked during verification
 */
@ConfigurationProperties(prefix = "bookstore.jwt")
public record JwtProperties(
        String secret,
        Duration expiration,
        String issuer
) {
}
