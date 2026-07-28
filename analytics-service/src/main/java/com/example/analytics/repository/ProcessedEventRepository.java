package com.example.analytics.repository;

import com.example.analytics.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

    @Modifying
    @Query(value = """
            INSERT INTO processed_events (event_id, order_id, processed_at)
            VALUES (:eventId, :orderId, :processedAt)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("eventId") String eventId,
                       @Param("orderId") Long orderId,
                       @Param("processedAt") Instant processedAt);
}
