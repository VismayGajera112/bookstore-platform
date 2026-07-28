package com.example.analytics.service;

import com.example.analytics.repository.ProcessedEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * See notification-service's {@code ProcessedEventGuard} for the full rationale: {@code REQUIRES_NEW}
 * so a duplicate-key failure never poisons the caller's persistence context.
 */
@Service
public class ProcessedEventGuard {

    private final ProcessedEventRepository repository;

    public ProcessedEventGuard(ProcessedEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markIfNew(String eventId, Long orderId) {
        return repository.insertIfAbsent(eventId, orderId, Instant.now()) == 1;
    }
}
