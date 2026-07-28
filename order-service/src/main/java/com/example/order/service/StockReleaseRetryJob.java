package com.example.order.service;

import com.example.order.client.CatalogGateway;
import com.example.order.entity.Order;
import com.example.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Settles compensations that could not be delivered while book-service was down.
 *
 * <p>This is the part that makes the saga eventually consistent rather than merely optimistic. A
 * cancelled order whose stock was never released leaves the two databases disagreeing: order_db says
 * the order is dead, book_db still has the units held. Retrying until book-service acknowledges is what
 * closes that window — and it works only because the release is idempotent, so a retry after a reply
 * that was lost in transit is harmless.
 *
 * <p>With more than one instance running, this needs a lock (a {@code SELECT … FOR UPDATE SKIP LOCKED}
 * claim, or ShedLock) so two schedulers do not process the same row; single instance for now.
 */
@Component
public class StockReleaseRetryJob {

    private static final Logger log = LoggerFactory.getLogger(StockReleaseRetryJob.class);

    private final OrderRepository orderRepository;
    private final OrderStateWriter stateWriter;
    private final CatalogGateway catalogGateway;

    public StockReleaseRetryJob(OrderRepository orderRepository,
                               OrderStateWriter stateWriter,
                               CatalogGateway catalogGateway) {
        this.orderRepository = orderRepository;
        this.stateWriter = stateWriter;
        this.catalogGateway = catalogGateway;
    }

    @Scheduled(fixedDelayString = "${bookstore.saga.stock-release-retry-interval:30000}")
    @Transactional(readOnly = true)
    public void retryPendingReleases() {
        List<Order> pending = orderRepository.findPendingStockReleases();
        if (pending.isEmpty()) {
            return;
        }

        log.info("Retrying {} pending stock release(s)", pending.size());
        pending.forEach(this::retry);
    }

    private void retry(Order order) {
        try {
            if (catalogGateway.releaseStock(order.getId())) {
                stateWriter.markStockReleased(order.getId());
                log.info("Compensation settled: stock released for order {}", order.getId());
            }
            // false = catalog still down; leave stockReleasePending so the next tick retries.
        } catch (com.example.common.exception.ResourceNotFoundException
                 | com.example.common.exception.BusinessRuleException ex) {
            // book-service says there is nothing left to release (already released, or never reserved).
            // Clearing the flag stops the row from being retried forever.
            log.warn("Nothing left to release for order {}: {}", order.getId(), ex.getMessage());
            stateWriter.markStockReleased(order.getId());
        } catch (RuntimeException ex) {
            // Unexpected failure — keep the debt so a later tick or a human can settle it.
            log.warn("Will retry releasing stock for order {}: {}", order.getId(), ex.getMessage());
        }
    }
}
