package com.example.order.event;

import com.example.order.entity.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The seam where Step 7 plugs Kafka in. Today it only logs, but the call sites are already in the
 * right places, so introducing a broker becomes a change of implementation rather than a change of
 * flow.
 *
 * <p>Publishing after the local transaction commits is deliberate: an event announcing an order that
 * later rolled back cannot be recalled. Doing it properly under a broker needs an outbox table, since
 * "write the row" and "publish the event" are two systems and one of them can fail.
 */
@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    public void orderCreated(Order order) {
        log.info("EVENT OrderCreated: orderId={} userId={} total={} items={}",
                order.getId(), order.getUserId(), order.getTotalAmount(), order.getItems().size());
    }

    public void orderPaid(Order order) {
        log.info("EVENT OrderPaid: orderId={} userId={} paymentId={}",
                order.getId(), order.getUserId(), order.getPaymentId());
    }

    public void orderCancelled(Order order, String reason) {
        log.info("EVENT OrderCancelled: orderId={} userId={} reason={}",
                order.getId(), order.getUserId(), reason);
    }
}
