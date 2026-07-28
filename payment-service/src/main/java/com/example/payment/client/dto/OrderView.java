package com.example.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/** Only the fields payment-service needs; the rest of order-service's response is ignored. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderView(Long id, Long userId, String status, BigDecimal totalAmount) {
}
