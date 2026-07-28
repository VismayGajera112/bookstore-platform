package com.example.notification.service;

import com.example.notification.repository.ProcessedEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * The idempotency check, isolated in its own transaction. {@code REQUIRES_NEW} matters here: a unique
 * constraint violation leaves the JPA persistence context unusable for the rest of that transaction, so
 * catching it and continuing in the caller's transaction would be fragile. Running in a fresh
 * transaction that starts, fails (or succeeds) and commits/rolls back entirely on its own avoids that.
 */
@Service
public class ProcessedEventGuard {

    private final ProcessedEventRepository repository;

    public ProcessedEventGuard(ProcessedEventRepository repository) {
        this.repository = repository;
    }

    /**
     * @return {@code true} the first time this event id is seen, {@code false} if it has already been
     *         recorded — i.e. this delivery is a duplicate and the caller should skip processing.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markIfNew(String eventId, Long orderId) {
        return repository.insertIfAbsent(eventId, orderId, Instant.now()) == 1;
    }
}
