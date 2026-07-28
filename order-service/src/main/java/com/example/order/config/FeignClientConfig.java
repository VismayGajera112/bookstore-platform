package com.example.order.config;

import com.example.common.exception.BusinessRuleException;
import com.example.common.exception.ResourceNotFoundException;
import com.example.order.exception.CatalogUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Identity propagation and error translation for calls into book-service.
 *
 * <p>Not annotated {@code @Configuration}-scanned by accident: it is referenced explicitly from
 * {@code @FeignClient}, so these beans apply to that client only.
 */
@Configuration
public class FeignClientConfig {

    private static final Logger log = LoggerFactory.getLogger(FeignClientConfig.class);

    /**
     * Forwards the caller's JWT downstream. book-service then authenticates the real customer rather
     * than a generic service account, so its own rules ("reservations require an authenticated user")
     * apply to the person who actually placed the order.
     *
     * <p>The token is read from the inbound request, which means it only exists on a request thread.
     * Background work (the compensation sweeper) therefore uses a service token instead.
     */
    @Bean
    public RequestInterceptor authorizationForwardingInterceptor(ServiceTokenProvider serviceTokenProvider) {
        return template -> {
            String authorization = inboundAuthorizationHeader();
            if (authorization == null) {
                authorization = "Bearer " + serviceTokenProvider.token();
                log.debug("No inbound request context; using the service token for {}", template.path());
            }
            template.header(HttpHeaders.AUTHORIZATION, authorization);
        };
    }

    private String inboundAuthorizationHeader() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
        }
        return null;
    }

    /**
     * Splits book-service's replies into two categories that resilience must treat differently:
     * a business answer is final and is re-thrown as the matching domain exception, while a transport
     * or server failure becomes {@link CatalogUnavailableException} — the only kind worth retrying.
     */
    @Bean
    public ErrorDecoder catalogErrorDecoder(ObjectMapper objectMapper) {
        return (methodKey, response) -> {
            String message = extractMessage(objectMapper, response);
            int status = response.status();

            return switch (status) {
                case 400 -> new IllegalArgumentException("book-service rejected the request: " + message);
                case 404 -> new ResourceNotFoundException(message);
                case 409 -> new BusinessRuleException(message);
                case 401, 403 -> new CatalogUnavailableException(
                        "book-service rejected this call's credentials: " + message);
                default -> new CatalogUnavailableException(
                        "book-service returned HTTP %d: %s".formatted(status, message));
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
