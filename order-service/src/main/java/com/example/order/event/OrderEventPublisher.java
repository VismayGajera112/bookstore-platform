package com.example.order.event;

import com.example.order.entity.Order;
import com.example.order.entity.OrderItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Publishes {@code OrderPlaced} to Kafka once stock has been reserved for an order — the point at
 * which the order is durably "placed" from the customer's perspective. {@code placeOrder()} returns to
 * the caller without waiting for this: {@link KafkaTemplate#send} is fire-and-forget, so a slow or
 * momentarily unavailable broker never adds latency to the HTTP response.
 *
 * <p>Publishing after the local transaction commits is deliberate: an event announcing an order that
 * later rolled back cannot be recalled. Doing this properly under heavier load needs an outbox table,
 * since "write the row" and "publish the event" are two systems and one of them can fail; that is left
 * for later — today a publish failure is logged and the order itself is unaffected.
 *
 * <p>Records are keyed by order id, so Kafka keeps every event for a given order on the same partition
 * and in publish order — useful once {@code OrderPaid} / {@code OrderCancelled} also become events.
 */
@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final String orderPlacedTopic;

    public OrderEventPublisher(KafkaTemplate<Object, Object> kafkaTemplate,
                               @Value("${app.kafka.topics.order-placed}") String orderPlacedTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.orderPlacedTopic = orderPlacedTopic;
    }

    public void orderCreated(Order order) {
        OrderPlacedEvent event = toEvent(order);
        String key = String.valueOf(order.getId());

        kafkaTemplate.send(orderPlacedTopic, key, event).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish OrderPlaced for order {}: {}", order.getId(), ex.getMessage(), ex);
            } else {
                log.info("Published OrderPlaced: orderId={} eventId={} partition={} offset={}",
                        order.getId(), event.eventId(),
                        result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            }
        });
    }

    public void orderPaid(Order order) {
        log.info("EVENT OrderPaid: orderId={} userId={} paymentId={}",
                order.getId(), order.getUserId(), order.getPaymentId());
    }

    public void orderCancelled(Order order, String reason) {
        log.info("EVENT OrderCancelled: orderId={} userId={} reason={}",
                order.getId(), order.getUserId(), reason);
    }

    private OrderPlacedEvent toEvent(Order order) {
        List<OrderPlacedEvent.Item> items = order.getItems().stream()
                .map(this::toItem)
                .toList();
        return new OrderPlacedEvent(
                UUID.randomUUID().toString(),
                order.getId(),
                order.getUserId(),
                order.getUsername(),
                order.getTotalAmount(),
                items,
                Instant.now());
    }

    private OrderPlacedEvent.Item toItem(OrderItem item) {
        return new OrderPlacedEvent.Item(item.getBookId(), item.getBookTitle(), item.getQuantity(), item.getUnitPrice());
    }
}
