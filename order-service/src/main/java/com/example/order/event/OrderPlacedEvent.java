package com.example.order.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The wire format order-service publishes to the {@code order-placed} topic. This is order-service's
 * schema to change; consumers (notification-service, analytics-service, ...) keep their own copy of
 * the fields they care about and tolerate unknown ones, so this class can gain fields without a
 * coordinated release.
 *
 * <p>{@code eventId} exists purely for de-duplication: Kafka's at-least-once delivery means any
 * consumer may see the same record more than once (a rebalance, a retry, a restart before an offset
 * commit lands), and an idempotent consumer uses this id to recognize a replay.
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
