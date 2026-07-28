package com.example.common.exception;

/**
 * A dependency of this service could not be reached (HTTP 503).
 *
 * <p>Thrown by resilience fallbacks where continuing would be unsafe — for example placing an order
 * when the catalog cannot confirm price or stock. Failing fast with a clear, retryable answer is what
 * stops one service's outage from cascading into another's.
 */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
