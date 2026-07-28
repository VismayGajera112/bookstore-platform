package com.example.book.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * The reservation contract order-service calls when placing an order. Multi-line so the whole order
 * is one atomic decision inside book-service, and keyed by {@code orderId} so the operation is
 * idempotent — a Resilience4j retry of a request that actually succeeded must not decrement twice.
 */
public record StockReservationRequest(

        @NotNull(message = "orderId is required")
        Long orderId,

        @NotEmpty(message = "items must not be empty")
        @Valid
        List<Item> items
) {

    public record Item(

            @NotNull(message = "bookId is required")
            Long bookId,

            @NotNull(message = "quantity is required")
            @Min(value = 1, message = "quantity must be at least 1")
            Integer quantity
    ) {
    }
}
