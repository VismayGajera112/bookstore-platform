package com.example.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Only book ids and quantities. Prices are never accepted from the client — order-service asks
 * book-service what each book costs, because the catalog owns that fact.
 */
public record PlaceOrderRequest(

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
