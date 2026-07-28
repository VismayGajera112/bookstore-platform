package com.example.payment.dto;

import com.example.payment.entity.Payment;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long id,
        Long orderId,
        Long userId,
        BigDecimal amount,
        String status,
        String cardLast4,
        String failureReason,
        boolean orderNotified,
        Instant createdAt
) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getUserId(),
                payment.getAmount(),
                payment.getStatus().name(),
                payment.getCardLast4(),
                payment.getFailureReason(),
                payment.isOrderNotified(),
                payment.getCreatedAt());
    }
}
