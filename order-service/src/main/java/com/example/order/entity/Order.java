package com.example.order.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * An order in order_db. Note what it stores as plain values rather than relationships:
 * {@code userId} (owned by user-service) and each item's {@code bookId}, title and price (owned by
 * book-service). Database-per-service forbids the foreign keys, and copying title and unit price at
 * purchase time is not just a workaround — an invoice must show what the customer actually paid, even
 * after the catalog changes the price.
 */
@Entity
@Table(name = "orders")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Copied from the JWT's {@code uid} claim; no foreign key to user-service is possible. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    /** Whether book-service currently holds stock for this order — the saga's compensation flag. */
    @Column(name = "stock_reserved", nullable = false)
    @Builder.Default
    private boolean stockReserved = false;

    /**
     * Set when a compensating release could not be delivered (book-service unreachable). A background
     * sweeper retries these, which is what makes the compensation eventually consistent instead of
     * silently lost.
     */
    @Column(name = "stock_release_pending", nullable = false)
    @Builder.Default
    private boolean stockReleasePending = false;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "payment_id")
    private Long paymentId;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public BigDecimal recalculateTotal() {
        BigDecimal total = items.stream()
                .map(OrderItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.totalAmount = total;
        return total;
    }
}
