package com.example.book.repository;

import com.example.book.entity.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    @Query("SELECT r FROM StockReservation r LEFT JOIN FETCH r.lines WHERE r.orderId = :orderId")
    Optional<StockReservation> findByOrderIdWithLines(@Param("orderId") Long orderId);
}
