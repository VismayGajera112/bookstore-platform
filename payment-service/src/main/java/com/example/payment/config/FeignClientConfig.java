package com.example.payment.config;

import com.example.common.exception.BusinessRuleException;
import com.example.common.exception.ResourceNotFoundException;
import com.example.payment.exception.OrderServiceUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.RequestInterceptor;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
public class FeignClientConfig {

    /** Forwards the customer's token so order-service authorizes the real owner of the order. */
    @Bean
    public RequestInterceptor authorizationForwardingInterceptor(ServiceTokenProvider serviceTokenProvider) {
        return template -> {
            String authorization = inboundAuthorizationHeader();
            template.header(HttpHeaders.AUTHORIZATION,
                    authorization != null ? authorization : "Bearer " + serviceTokenProvider.token());
        };
    }

    private String inboundAuthorizationHeader() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
        }
        return null;
    }

    @Bean
    public ErrorDecoder orderErrorDecoder(ObjectMapper objectMapper) {
        return (methodKey, response) -> {
            String message = extractMessage(objectMapper, response);
            return switch (response.status()) {
                case 400 -> new IllegalArgumentException("order-service rejected the request: " + message);
                case 403 -> new AccessDeniedException(message);
                case 404 -> new ResourceNotFoundException(message);
                case 409 -> new BusinessRuleException(message);
                default -> new OrderServiceUnavailableException(
                        "order-service returned HTTP %d: %s".formatted(response.status(), message));
            };
        };
    }

    private String extractMessage(ObjectMapper objectMapper, Response response) {
        if (response.body() == null) {
            return "no response body";
        }
        try {
            String body = new String(response.body().asInputStream().readAllBytes(), StandardCharsets.UTF_8);
            var node = objectMapper.readTree(body);
            return node.hasNonNull("message") ? node.get("message").asText() : body;
        } catch (IOException ex) {
            return "unreadable response body";
        }
    }
}
