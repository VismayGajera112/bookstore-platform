package com.example.order.client.dto;

import java.math.BigDecimal;

/**
 * order-service's own copy of the fields it needs from book-service's availability response.
 *
 * <p>Duplicating the record instead of sharing one class keeps the services independently deployable:
 * book-service can add fields to its DTO without recompiling order-service, and Jackson ignores
 * anything unknown.
 */
public record BookAvailability(Long id, String title, BigDecimal price, Integer stock) {
}
