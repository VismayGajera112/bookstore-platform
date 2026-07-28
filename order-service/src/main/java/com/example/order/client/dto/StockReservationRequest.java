package com.example.order.client.dto;

import java.util.List;

public record StockReservationRequest(Long orderId, List<Item> items) {

    public record Item(Long bookId, Integer quantity) {
    }
}
