package com.example.payment.config;

import com.example.common.security.JwtUtil;
import org.springframework.stereotype.Component;

/**
 * Used only by the redelivery sweeper, which has no inbound request whose token it could forward. It
 * asks for ADMIN because order-service's owner-or-admin rule would otherwise reject a callback about
 * another user's order — a background process has no user to impersonate.
 */
@Component
public class ServiceTokenProvider {

    private static final String SERVICE_PRINCIPAL = "system:payment-service";
    private static final long SERVICE_USER_ID = 0L;

    private final JwtUtil jwtUtil;

    public ServiceTokenProvider(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    public String token() {
        return jwtUtil.generateToken(SERVICE_USER_ID, SERVICE_PRINCIPAL, "ADMIN");
    }
}
