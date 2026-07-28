package com.example.notification.listener;

import com.example.notification.event.OrderPlacedEvent;
import com.example.notification.service.ProcessedEventGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code order-placed} under the {@code notification} consumer group — independent of
 * analytics-service's group, so both see every event without coordinating with each other or with
 * order-service.
 *
 * <p>At-least-once delivery means the same record can be handed to this listener more than once (a
 * consumer-group rebalance before an offset commits, a container restart mid-batch, a retry after a
 * transient failure). {@link ProcessedEventGuard} is what makes a repeat a silent no-op instead of a
 * second confirmation email. A record that still fails after retries is routed to the
 * {@code order-placed.DLT} topic by the error handler configured in {@code KafkaConsumerConfig} — it
 * never blocks this partition forever.
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
            log.info("Duplicate OrderPlaced event {} for order {} ignored (already processed)",
                    event.eventId(), event.orderId());
            return;
        }

        log.info("Confirmation sent to '{}': order {} placed, {} item(s), total {}",
                event.username(), event.orderId(), event.items().size(), event.totalAmount());
    }
}
