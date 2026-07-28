package com.example.notification.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * notification-service's own view of what order-service publishes to {@code order-placed}. Only the
 * fields this service actually uses are declared here; the JSON deserializer is configured to ignore
 * unknown properties, so order-service can add fields to its event without breaking this consumer
 * (tolerant reader).
 */
public record OrderPlacedEvent(
        String eventId,
        Long orderId,
        Long userId,
        String username,
        BigDecimal totalAmount,
        List<Item> items,
        Instant occurredAt) {

    public record Item(Long bookId, String bookTitle, Integer quantity, BigDecimal unitPrice) {
    }
}
