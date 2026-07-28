package com.example.common.security;

import com.example.common.web.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Spring Security rejects requests inside the filter chain, before any {@code @RestControllerAdvice}
 * runs, so 401 and 403 responses need their own writers to keep the platform's JSON error shape.
 */
public final class JsonAuthenticationErrorHandlers {

    private JsonAuthenticationErrorHandlers() {
    }

    public static AuthenticationEntryPoint unauthorized(ObjectMapper objectMapper, String serviceName) {
        return (request, response, exception) -> write(objectMapper, response, request.getRequestURI(), serviceName,
                HttpStatus.UNAUTHORIZED, "Authentication required: provide a valid Bearer token");
    }

    public static AccessDeniedHandler forbidden(ObjectMapper objectMapper, String serviceName) {
        return (request, response, exception) -> write(objectMapper, response, request.getRequestURI(), serviceName,
                HttpStatus.FORBIDDEN, "Access denied: this action requires a different role");
    }

    private static void write(ObjectMapper objectMapper,
                              jakarta.servlet.http.HttpServletResponse response,
                              String path,
                              String serviceName,
                              HttpStatus status,
                              String message) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ErrorResponse.of(status.value(), status.getReasonPhrase(), message, path, serviceName));
    }
}
