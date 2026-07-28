package com.example.payment.event;

import com.example.payment.entity.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Where {@code PaymentCompleted} will be published to Kafka in Step 7. Until then the same fact travels
 * as a direct callback to order-service, which is simpler but couples the two services in time: both
 * must be up at the same moment. The event log below marks the seam.
 */
@Component
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);

    public void paymentCompleted(Payment payment) {
        log.info("EVENT PaymentCompleted: paymentId={} orderId={} userId={} amount={}",
                payment.getId(), payment.getOrderId(), payment.getUserId(), payment.getAmount());
    }

    public void paymentFailed(Payment payment) {
        log.info("EVENT PaymentFailed: paymentId={} orderId={} reason={}",
                payment.getId(), payment.getOrderId(), payment.getFailureReason());
    }
}
