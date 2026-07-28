package com.example.order.dto;

import com.example.order.entity.OrderItem;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long bookId,
        String bookTitle,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getBookId(),
                item.getBookTitle(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.lineTotal());
    }
}
