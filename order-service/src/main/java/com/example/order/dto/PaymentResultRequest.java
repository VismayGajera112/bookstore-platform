package com.example.order.dto;

import jakarta.validation.constraints.NotNull;

/**
 * The saga callback payment-service sends once it has a verdict. On SUCCESS the order moves to PAID;
 * on FAILURE order-service runs the compensating stock release and cancels the order.
 *
 * <p>Step 7 replaces this synchronous callback with a {@code PaymentCompleted} Kafka event, which
 * removes payment-service's dependency on order-service being reachable at that moment.
 */
public record PaymentResultRequest(

        @NotNull(message = "paymentId is required")
        Long paymentId,

        @NotNull(message = "status is required")
        Status status,

        String reason
) {

    public enum Status {
        SUCCESS,
        FAILURE
    }
}
