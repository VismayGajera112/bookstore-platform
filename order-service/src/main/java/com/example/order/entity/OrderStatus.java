package com.example.order.entity;

import java.util.Set;

/**
 * The saga's state machine, written down explicitly. Without a distributed transaction, an order's
 * status is the only record of how far the sequence got and what still has to be undone.
 *
 * <pre>
 *   PENDING ──reserve ok──► AWAITING_PAYMENT ──payment ok──► PAID ──► SHIPPED
 *      │                          │
 *      └──reserve failed──► CANCELLED ◄──payment failed / user cancel (stock released)
 * </pre>
 *
 * <p>PAID is not cancellable here: undoing a paid order needs a refund in payment-service, which
 * arrives with Step 7's event-driven saga. Until then, releasing stock on a paid cancel would leave
 * money taken and inventory restored — the databases disagreeing in the wrong direction.
 */
public enum OrderStatus {

    /** Order row committed, stock not yet reserved. Nothing to compensate if it stops here. */
    PENDING,

    /** book-service confirmed the reservation. Cancelling from here must release that stock. */
    AWAITING_PAYMENT,

    PAID,

    SHIPPED,

    CANCELLED;

    /**
     * Once an order is PAID the stock has been sold, not merely held. Cancelling it would require a
     * refund in payment-service — which this step does not yet implement — so PAID and SHIPPED are
     * deliberately excluded. Releasing inventory on a paid cancel without undoing the payment is how
     * the two databases drift.
     */
    private static final Set<OrderStatus> CANCELLABLE = Set.of(PENDING, AWAITING_PAYMENT);

    public boolean isCancellable() {
        return CANCELLABLE.contains(this);
    }
}
