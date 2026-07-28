package com.example.payment.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The wire format payment-service publishes to the {@code payment-completed} topic. No consumer group
 * reads this yet in Step 7 — order-service still learns the verdict via the synchronous callback in
 * {@code OrderGateway} — but the seam is here so a future consumer (e.g. accounting, fraud review) can
 * be added without touching payment-service again.
 */
public record PaymentCompletedEvent(
        String eventId,
        Long paymentId,
        Long orderId,
        Long userId,
        BigDecimal amount,
        Instant occurredAt) {
}
