package com.example.book.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * book-service's record of stock it has already handed out to an order, and the key to two things
 * the saga needs.
 *
 * <p>Idempotency: {@code order_id} is unique, so a retried reserve call for an order that already
 * succeeded returns the existing reservation instead of decrementing stock a second time.
 *
 * <p>Compensation: the reserved quantities are stored here, so a release only needs the order id —
 * order-service does not have to remember and resend the lines it once reserved.
 *
 * <p>This is deliberately book-service's own state, not a copy of the order. It holds no price, no
 * customer and no status beyond the reservation's own lifecycle.
 */
@Entity
@Table(name = "stock_reservation")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockReservation {

    public enum Status {
        RESERVED,
        RELEASED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The order this stock belongs to, owned by order-service; no foreign key can exist across DBs. */
    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "stock_reservation_item",
            joinColumns = @JoinColumn(name = "reservation_id", nullable = false))
    @Builder.Default
    private List<Line> lines = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Line {

        @Column(name = "book_id", nullable = false)
        private Long bookId;

        @Column(nullable = false)
        private Integer quantity;
    }
}
