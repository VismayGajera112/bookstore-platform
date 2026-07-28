package com.example.gateway.filter;

import com.example.common.security.AuthenticatedUser;
import com.example.common.security.JwtUtil;
import com.example.common.web.ErrorResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Validates JWTs at the edge before a request is proxied downstream. Public routes pass through;
 * protected routes without a valid token are rejected here so backend services never see them.
 *
 * <p>Verified identity is forwarded as the original {@code Authorization} header plus
 * {@code X-User-Id}, {@code X-Username} and {@code X-User-Role} for services that prefer headers.
 */
@Component
public class JwtValidationFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final String serviceName;

    public JwtValidationFilter(JwtUtil jwtUtil,
                               ObjectMapper objectMapper,
                               @Value("${spring.application.name:api-gateway}") String serviceName) {
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
        this.serviceName = serviceName;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        HttpMethod method = request.getMethod();
        String path = request.getURI().getPath();

        if (method == HttpMethod.OPTIONS || isActuatorHealth(path)) {
            return chain.filter(exchange);
        }

        if (isPublic(method, path)) {
            return forwardWithOptionalIdentity(exchange, chain, request);
        }

        String token = extractBearerToken(request);
        if (token == null) {
            return reject(exchange, HttpStatus.UNAUTHORIZED,
                    "Authentication required: provide a valid Bearer token");
        }

        Optional<AuthenticatedUser> user = jwtUtil.authenticate(token);
        if (user.isEmpty()) {
            return reject(exchange, HttpStatus.UNAUTHORIZED,
                    "Authentication required: provide a valid Bearer token");
        }

        if (requiresAdmin(method, path) && !user.get().isAdmin()) {
            return reject(exchange, HttpStatus.FORBIDDEN,
                    "Access denied: this action requires a different role");
        }

        return chain.filter(exchange.mutate().request(enrichRequest(request, token, user.get())).build());
    }

    private Mono<Void> forwardWithOptionalIdentity(ServerWebExchange exchange,
                                                   GatewayFilterChain chain,
                                                   ServerHttpRequest request) {
        String token = extractBearerToken(request);
        if (token == null) {
            return chain.filter(exchange);
        }

        Optional<AuthenticatedUser> user = jwtUtil.authenticate(token);
        if (user.isPresent()) {
            ServerWebExchange enriched = exchange.mutate()
                    .request(enrichRequest(request, token, user.get()))
                    .build();
            return chain.filter(enriched);
        }
        return chain.filter(exchange);
    }

    private ServerHttpRequest enrichRequest(ServerHttpRequest request,
                                            String token,
                                            AuthenticatedUser user) {
        return request.mutate()
                .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + token)
                .header("X-User-Id", String.valueOf(user.userId()))
                .header("X-Username", user.username())
                .header("X-User-Role", user.role())
                .build();
    }

    private boolean isPublic(HttpMethod method, String path) {
        if (method == HttpMethod.POST && matchesAny(path, "/api/auth/register", "/api/auth/login")) {
            return true;
        }
        if (method == HttpMethod.GET && matchesAny(path, "/api/books/**", "/api/authors/**")
                && !PATH_MATCHER.match("/api/books/availability", path)
                && !PATH_MATCHER.match("/api/books/me/history", path)
                && !PATH_MATCHER.match("/api/books/me/history/**", path)) {
            return true;
        }
        return false;
    }

    private boolean requiresAdmin(HttpMethod method, String path) {
        if (method == null) {
            return false;
        }
        return switch (method.name()) {
            case "POST", "PUT", "DELETE" -> matchesAny(path, "/api/books/**", "/api/authors/**");
            default -> false;
        };
    }

    private boolean isActuatorHealth(String path) {
        return PATH_MATCHER.match("/actuator/health/**", path)
                || PATH_MATCHER.match("/actuator/info", path)
                || PATH_MATCHER.match("/actuator/prometheus", path);
    }

    private boolean matchesAny(String path, String... patterns) {
        for (String pattern : patterns) {
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private String extractBearerToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ErrorResponse body = ErrorResponse.of(
                status.value(),
                status.getReasonPhrase(),
                message,
                exchange.getRequest().getURI().getPath(),
                serviceName);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException ex) {
            bytes = ("{\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
