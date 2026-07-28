package com.example.order.config;

import com.example.common.security.JwtUtil;
import org.springframework.stereotype.Component;

/**
 * Mints a token for work that has no inbound request to forward — the compensation sweeper retrying a
 * stock release after book-service comes back.
 *
 * <p>order-service can self-issue because the platform uses one symmetric signing secret, which is
 * convenient and also the weakness of that choice: any service holding the secret can mint any
 * identity. Asymmetric keys (user-service signs with a private key, others verify with the public one)
 * or a client-credentials grant would remove that power; the shared secret stays for now because
 * Step 6 moves configuration to a config server first.
 */
@Component
public class ServiceTokenProvider {

    private static final String SERVICE_PRINCIPAL = "system:order-service";
    private static final long SERVICE_USER_ID = 0L;

    private final JwtUtil jwtUtil;

    public ServiceTokenProvider(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    /** A short-lived token with the USER role — the least it needs to release a reservation. */
    public String token() {
        return jwtUtil.generateToken(SERVICE_USER_ID, SERVICE_PRINCIPAL, "USER");
    }
}
