package com.example.order.repository;

import com.example.order.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /** Fetch join on items: an order is always rendered with its lines, so never load them lazily. */
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id = :id")
    Optional<Order> findByIdWithItems(@Param("id") Long id);

    @Query(value = "SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.userId = :userId",
            countQuery = "SELECT count(o) FROM Order o WHERE o.userId = :userId")
    Page<Order> findByUserIdWithItems(@Param("userId") Long userId, Pageable pageable);

    @Query(value = "SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items",
            countQuery = "SELECT count(o) FROM Order o")
    Page<Order> findAllWithItems(Pageable pageable);

    /** Work list for the compensation sweeper. */
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.stockReleasePending = true")
    List<Order> findPendingStockReleases();
}
