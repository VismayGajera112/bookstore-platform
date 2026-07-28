package com.example.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Issues and verifies HMAC-SHA JSON Web Tokens. JJWT picks the strongest algorithm the key length
 * allows, so a 32-byte secret yields HS256 and a longer one HS384/HS512.
 *
 * <p>Verification recomputes the signature from the header and payload using the shared secret and
 * checks {@code exp} and {@code iss}. No service consults a session store or calls user-service to
 * validate a token — that independence is the point: user-service can be down and the other services
 * still authenticate requests.
 */
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_USER_ID = "uid";

    private final SecretKey signingKey;
    private final Duration expiration;
    private final String issuer;

    public JwtUtil(JwtProperties properties) {
        byte[] keyBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "bookstore.jwt.secret must be at least 32 bytes for HMAC-SHA (got " + keyBytes.length + ")");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expiration = properties.expiration() == null ? Duration.ofHours(1) : properties.expiration();
        this.issuer = properties.issuer();
    }

    public String generateToken(Long userId, String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .issuer(issuer)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_ROLE, role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(signingKey)
                .compact();
    }

    /** Returns verified claims, or empty when the token is malformed, forged or expired. */
    public Optional<Claims> parse(String token) {
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected JWT: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public Optional<AuthenticatedUser> authenticate(String token) {
        return parse(token).map(claims -> new AuthenticatedUser(
                claims.get(CLAIM_USER_ID, Number.class).longValue(),
                claims.getSubject(),
                claims.get(CLAIM_ROLE, String.class)));
    }

    public boolean isValid(String token) {
        return parse(token).isPresent();
    }

    public long expiresInSeconds() {
        return expiration.toSeconds();
    }
}
