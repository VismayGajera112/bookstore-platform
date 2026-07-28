package com.example.order.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import io.github.resilience4j.retry.RetryRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

/**
 * The circuit breaker and retry instances themselves are declared in {@code application.yml}, where
 * their thresholds belong. This class adds the operational half: every state transition is logged, so
 * "the breaker opened" is visible in the log rather than inferred from a pile of 503s.
 *
 * <p>Why the thresholds are what they are, in one place:
 * <ul>
 *   <li><b>Timeout (2s read, 1s connect)</b> — a call that has not answered in two seconds will not
 *       save the request; holding the thread only spreads book-service's problem into this service.</li>
 *   <li><b>Retry: 2 attempts, 200ms apart, exponential</b> — enough to ride out a dropped connection
 *       or a restart, few enough not to amplify load on a service that is already struggling. Only
 *       transport failures are retried; "out of stock" is a final answer.</li>
 *   <li><b>Breaker: 50% of 10 calls, 10s open</b> — once half the recent calls fail, further calls
 *       fail immediately instead of each waiting for its own timeout. That is the difference between a
 *       degraded feature and an exhausted thread pool.</li>
 * </ul>
 */
@Configuration
public class ResilienceConfig {

    private static final Logger log = LoggerFactory.getLogger(ResilienceConfig.class);

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;

    public ResilienceConfig(CircuitBreakerRegistry circuitBreakerRegistry, RetryRegistry retryRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
    }

    @PostConstruct
    public void registerEventLogging() {
        circuitBreakerRegistry.getEventPublisher()
                .onEntryAdded(event -> attachStateLogging(event.getAddedEntry()));
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(this::attachStateLogging);

        retryRegistry.retry("bookService").getEventPublisher()
                .onRetry(event -> log.warn("Retry {} for {} after {}",
                        event.getNumberOfRetryAttempts(), event.getName(),
                        event.getLastThrowable() == null ? "unknown failure"
                                : event.getLastThrowable().getMessage()));
    }

    private void attachStateLogging(CircuitBreaker circuitBreaker) {
        circuitBreaker.getEventPublisher().onStateTransition(this::logTransition);
    }

    private void logTransition(CircuitBreakerOnStateTransitionEvent event) {
        log.warn("Circuit breaker '{}' {} -> {}",
                event.getCircuitBreakerName(),
                event.getStateTransition().getFromState(),
                event.getStateTransition().getToState());
    }
}
