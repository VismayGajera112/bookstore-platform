package com.example.payment.client.dto;

public record PaymentResultRequest(Long paymentId, String status, String reason) {

    public static PaymentResultRequest success(Long paymentId) {
        return new PaymentResultRequest(paymentId, "SUCCESS", null);
    }

    public static PaymentResultRequest failure(Long paymentId, String reason) {
        return new PaymentResultRequest(paymentId, "FAILURE", reason);
    }
}
