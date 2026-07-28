package com.example.book.dto;

import com.example.book.entity.StockReservation;

import java.time.Instant;
import java.util.List;

public record StockReservationResponse(
        Long orderId,
        String status,
        List<Line> lines,
        Instant createdAt,
        Instant releasedAt
) {

    public record Line(Long bookId, Integer quantity, Integer remainingStock) {
    }

    public static StockReservationResponse from(StockReservation reservation, List<Line> lines) {
        return new StockReservationResponse(
                reservation.getOrderId(),
                reservation.getStatus().name(),
                lines,
                reservation.getCreatedAt(),
                reservation.getReleasedAt());
    }
}
