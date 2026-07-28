package com.example.payment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * There is no real gateway here. {@code simulateFailure} makes the unhappy path reproducible on demand,
 * which is the only way to demonstrate the compensating half of the saga.
 */
public record PaymentRequest(

        @NotNull(message = "orderId is required")
        Long orderId,

        @Pattern(regexp = "\\d{4}", message = "cardLast4 must be 4 digits")
        String cardLast4,

        boolean simulateFailure
) {
}
