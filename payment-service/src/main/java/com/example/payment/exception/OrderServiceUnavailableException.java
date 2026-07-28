package com.example.payment.exception;

/** order-service could not be reached or failed with a server error. Retryable. */
public class OrderServiceUnavailableException extends RuntimeException {

    public OrderServiceUnavailableException(String message) {
        super(message);
    }

    public OrderServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
