package com.example.order.client;

import com.example.common.exception.ServiceUnavailableException;
import com.example.order.client.dto.BookAvailability;
import com.example.order.client.dto.StockReservationRequest;
import com.example.order.client.dto.StockReservationResponse;
import com.example.order.exception.CatalogUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Everything order-service knows about talking to book-service, with the resilience policy attached.
 *
 * <p>The gateway exists so the policy sits in one place instead of on the {@link BookClient} interface:
 * it can translate transport failures before the circuit breaker sees them, and it can decide per
 * operation what "degrade gracefully" means. Those decisions differ:
 * <ul>
 *   <li><b>Availability and reserve</b> fail fast with 503. There is no safe fallback — guessing a
 *       price or assuming stock exists would sell books the shop does not have.</li>
 *   <li><b>Release</b> returns false instead of throwing, because the cancellation must still succeed
 *       for the customer; the unreleased stock is recorded and retried later.</li>
 * </ul>
 */
@Component
public class CatalogGateway {

    private static final Logger log = LoggerFactory.getLogger(CatalogGateway.class);
    private static final String CIRCUIT = "bookService";

    private final BookClient bookClient;

    public CatalogGateway(BookClient bookClient) {
        this.bookClient = bookClient;
    }

    @CircuitBreaker(name = CIRCUIT, fallbackMethod = "availabilityFallback")
    @Retry(name = CIRCUIT)
    public List<BookAvailability> getAvailability(List<Long> bookIds) {
        return translateFailures(() -> bookClient.getAvailability(bookIds));
    }

    @CircuitBreaker(name = CIRCUIT, fallbackMethod = "reserveFallback")
    @Retry(name = CIRCUIT)
    public StockReservationResponse reserveStock(StockReservationRequest request) {
        return translateFailures(() -> bookClient.reserveStock(request));
    }

    /**
     * @return true when book-service confirmed the release; false when it could not be reached, so the
     *         caller can mark the compensation as still owed
     */
    @CircuitBreaker(name = CIRCUIT, fallbackMethod = "releaseFallback")
    @Retry(name = CIRCUIT)
    public boolean releaseStock(Long orderId) {
        translateFailures(() -> bookClient.releaseStock(orderId));
        return true;
    }

    @SuppressWarnings("unused")
    private List<BookAvailability> availabilityFallback(List<Long> bookIds, Throwable failure) {
        rethrowBusinessAnswers(failure);
        throw unavailable("price and stock cannot be confirmed", failure);
    }

    @SuppressWarnings("unused")
    private StockReservationResponse reserveFallback(StockReservationRequest request, Throwable failure) {
        rethrowBusinessAnswers(failure);
        throw unavailable("stock cannot be reserved", failure);
    }

    @SuppressWarnings("unused")
    private boolean releaseFallback(Long orderId, Throwable failure) {
        rethrowBusinessAnswers(failure);
        log.warn("Could not release stock for order {} ({}); it will be retried in the background",
                orderId, describe(failure));
        return false;
    }

    /**
     * A fallback fires for every exception, including book-service's business answers. Those are real
     * results, not outages, so they are handed straight back to the caller and only genuine
     * unavailability is turned into degraded behaviour.
     */
    private void rethrowBusinessAnswers(Throwable failure) {
        boolean unavailable = failure instanceof CatalogUnavailableException
                || failure instanceof CallNotPermittedException;
        if (!unavailable && failure instanceof RuntimeException businessAnswer) {
            throw businessAnswer;
        }
    }

    private ServiceUnavailableException unavailable(String consequence, Throwable failure) {
        String message = "The book catalog is temporarily unavailable, so %s. Please retry shortly."
                .formatted(consequence);
        log.warn("{} Cause: {}", message, describe(failure));
        return new ServiceUnavailableException(message, failure);
    }

    private String describe(Throwable failure) {
        if (failure instanceof CallNotPermittedException) {
            // No call was even attempted: the breaker is open and is failing fast by design.
            return "circuit breaker '%s' is OPEN".formatted(CIRCUIT);
        }
        return "%s: %s".formatted(failure.getClass().getSimpleName(), failure.getMessage());
    }

    /**
     * Anything that is not a decoded business answer becomes {@link CatalogUnavailableException} before
     * the circuit breaker and retry see it, which is what makes their include/exclude lists reliable:
     * connection refused, socket timeout and HTTP 500 all arrive as one recognisable type.
     */
    private <T> T translateFailures(CatalogCall<T> call) {
        try {
            return call.execute();
        } catch (CatalogUnavailableException ex) {
            throw ex;
        } catch (feign.RetryableException ex) {
            throw new CatalogUnavailableException("book-service is unreachable: " + ex.getMessage(), ex);
        } catch (feign.FeignException ex) {
            throw new CatalogUnavailableException("book-service call failed: " + ex.getMessage(), ex);
        }
    }

    @FunctionalInterface
    private interface CatalogCall<T> {
        T execute();
    }
}
