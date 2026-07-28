package com.example.analytics.listener;

import com.example.analytics.event.OrderPlacedEvent;
import com.example.analytics.service.ProcessedEventGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code order-placed} under the {@code analytics} group. Because notification-service uses
 * a different group id, both services receive every record (fan-out) without coupling to each other.
 */
@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final ProcessedEventGuard processedEventGuard;

    public OrderEventListener(ProcessedEventGuard processedEventGuard) {
        this.processedEventGuard = processedEventGuard;
    }

    @KafkaListener(topics = "${app.kafka.topics.order-placed}", groupId = "${spring.kafka.consumer.group-id}")
    public void onOrderPlaced(OrderPlacedEvent event) {
        if (!processedEventGuard.markIfNew(event.eventId(), event.orderId())) {
            log.info("Duplicate OrderPlaced event {} for order {} ignored by analytics",
                    event.eventId(), event.orderId());
            return;
        }

        log.info("Analytics recorded OrderPlaced: orderId={} userId={} total={} items={} occurredAt={}",
                event.orderId(), event.userId(), event.totalAmount(), event.items().size(), event.occurredAt());
    }
}
