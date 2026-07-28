package com.example.order.dto;

import com.example.order.entity.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        Long userId,
        String username,
        String status,
        BigDecimal totalAmount,
        boolean stockReserved,
        boolean stockReleasePending,
        String statusReason,
        Long paymentId,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getUsername(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.isStockReserved(),
                order.isStockReleasePending(),
                order.getStatusReason(),
                order.getPaymentId(),
                order.getItems().stream().map(OrderItemResponse::from).toList(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
