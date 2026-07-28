package com.example.payment.client;

import com.example.common.exception.ServiceUnavailableException;
import com.example.payment.client.dto.OrderView;
import com.example.payment.client.dto.PaymentResultRequest;
import com.example.payment.exception.OrderServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Same shape of protection as order-service applies to the catalog, with the same split of intent:
 * reading the order fails fast, because paying without knowing the amount is not an option, while
 * reporting the verdict degrades to "not delivered yet" so a stored payment is never lost to a network
 * failure.
 */
@Component
public class OrderGateway {

    private static final Logger log = LoggerFactory.getLogger(OrderGateway.class);
    private static final String CIRCUIT = "orderService";

    private final OrderClient orderClient;

    public OrderGateway(OrderClient orderClient) {
        this.orderClient = orderClient;
    }

    @CircuitBreaker(name = CIRCUIT, fallbackMethod = "getOrderFallback")
    @Retry(name = CIRCUIT)
    public OrderView getOrder(Long orderId) {
        return translateFailures(() -> orderClient.getOrder(orderId));
    }

    /** @return true when order-service acknowledged the verdict */
    @CircuitBreaker(name = CIRCUIT, fallbackMethod = "reportFallback")
    @Retry(name = CIRCUIT)
    public boolean reportPaymentResult(Long orderId, PaymentResultRequest request) {
        translateFailures(() -> orderClient.reportPaymentResult(orderId, request));
        return true;
    }

    @SuppressWarnings("unused")
    private OrderView getOrderFallback(Long orderId, Throwable failure) {
        rethrowBusinessAnswers(failure);
        throw new ServiceUnavailableException(
                "Orders are temporarily unavailable, so this payment cannot be verified. Please retry shortly.",
                failure);
    }

    @SuppressWarnings("unused")
    private boolean reportFallback(Long orderId, PaymentResultRequest request, Throwable failure) {
        rethrowBusinessAnswers(failure);
        log.warn("Could not deliver the payment verdict for order {} ({}); it will be redelivered",
                orderId, failure.getMessage());
        return false;
    }

    private void rethrowBusinessAnswers(Throwable failure) {
        boolean unavailable = failure instanceof OrderServiceUnavailableException
                || failure instanceof CallNotPermittedException;
        if (!unavailable && failure instanceof RuntimeException businessAnswer) {
            throw businessAnswer;
        }
    }

    private <T> T translateFailures(OrderCall<T> call) {
        try {
            return call.execute();
        } catch (OrderServiceUnavailableException ex) {
            throw ex;
        } catch (feign.RetryableException ex) {
            throw new OrderServiceUnavailableException("order-service is unreachable: " + ex.getMessage(), ex);
        } catch (feign.FeignException ex) {
            throw new OrderServiceUnavailableException("order-service call failed: " + ex.getMessage(), ex);
        }
    }

    @FunctionalInterface
    private interface OrderCall<T> {
        T execute();
    }
}
