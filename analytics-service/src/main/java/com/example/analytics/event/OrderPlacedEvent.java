package com.example.analytics.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * analytics-service's own view of what order-service publishes to {@code order-placed}. Kept separate
 * from notification-service's copy on purpose — each consumer owns the shape it depends on, so one
 * service's needs never dictate the other's contract.
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
