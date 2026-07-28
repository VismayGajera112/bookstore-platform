package com.example.order.client.dto;

import java.util.List;

public record StockReservationResponse(Long orderId, String status, List<Line> lines) {

    public record Line(Long bookId, Integer quantity, Integer remainingStock) {
    }
}
