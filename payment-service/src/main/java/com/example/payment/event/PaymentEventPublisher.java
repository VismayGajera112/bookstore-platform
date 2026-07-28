package com.example.payment.event;

import com.example.payment.entity.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishes {@code PaymentCompleted} to Kafka, keyed by order id. The same fact still travels to
 * order-service as a direct callback (see {@code OrderGateway}) because the saga needs a definite
 * answer to move the order to PAID or compensate the reservation; the Kafka event is for everyone else
 * who might want to react to a completed payment without order-service or payment-service knowing they
 * exist.
 */
@Component
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final String paymentCompletedTopic;

    public PaymentEventPublisher(KafkaTemplate<Object, Object> kafkaTemplate,
                                 @Value("${app.kafka.topics.payment-completed}") String paymentCompletedTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.paymentCompletedTopic = paymentCompletedTopic;
    }

    public void paymentCompleted(Payment payment) {
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                UUID.randomUUID().toString(),
                payment.getId(),
                payment.getOrderId(),
                payment.getUserId(),
                payment.getAmount(),
                Instant.now());
        String key = String.valueOf(payment.getOrderId());

        kafkaTemplate.send(paymentCompletedTopic, key, event).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish PaymentCompleted for payment {}: {}",
                        payment.getId(), ex.getMessage(), ex);
            } else {
                log.info("Published PaymentCompleted: paymentId={} orderId={} partition={} offset={}",
                        payment.getId(), payment.getOrderId(),
                        result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            }
        });
    }

    public void paymentFailed(Payment payment) {
        log.info("EVENT PaymentFailed: paymentId={} orderId={} reason={}",
                payment.getId(), payment.getOrderId(), payment.getFailureReason());
    }
}
