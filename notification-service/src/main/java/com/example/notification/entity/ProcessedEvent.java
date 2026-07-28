package com.example.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One row per event id this service has successfully handled. Its presence — enforced by the primary
 * key being the event id itself — is what makes the listener idempotent: at-least-once delivery means
 * Kafka may redeliver the same record (a rebalance mid-commit, a restart before the offset commits),
 * and this table is what tells a replay apart from a new event.
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", length = 64)
    private String eventId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEvent() {
        // JPA
    }

    public ProcessedEvent(String eventId, Long orderId, Instant processedAt) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.processedAt = processedAt;
    }

    public String getEventId() {
        return eventId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
