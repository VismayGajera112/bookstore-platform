package com.example.payment.service;

import com.example.payment.client.OrderGateway;
import com.example.payment.client.dto.PaymentResultRequest;
import com.example.payment.entity.Payment;
import com.example.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Redelivers verdicts order-service never acknowledged, so a payment recorded while order-service was
 * down still finishes the saga once it returns. Safe to repeat because order-service treats a duplicate
 * result for an already-PAID order as a no-op.
 */
@Component
public class PaymentResultRedeliveryJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentResultRedeliveryJob.class);

    private final PaymentRepository paymentRepository;
    private final PaymentWriter paymentWriter;
    private final OrderGateway orderGateway;

    public PaymentResultRedeliveryJob(PaymentRepository paymentRepository,
                                      PaymentWriter paymentWriter,
                                      OrderGateway orderGateway) {
        this.paymentRepository = paymentRepository;
        this.paymentWriter = paymentWriter;
        this.orderGateway = orderGateway;
    }

    @Scheduled(fixedDelayString = "${bookstore.saga.payment-redelivery-interval:30000}")
    @Transactional(readOnly = true)
    public void redeliverUnacknowledgedResults() {
        List<Payment> pending = paymentRepository.findByOrderNotifiedFalse();
        if (pending.isEmpty()) {
            return;
        }

        log.info("Redelivering {} payment verdict(s)", pending.size());
        pending.forEach(this::redeliver);
    }

    private void redeliver(Payment payment) {
        PaymentResultRequest request = payment.getStatus() == Payment.Status.SUCCESS
                ? PaymentResultRequest.success(payment.getId())
                : PaymentResultRequest.failure(payment.getId(), payment.getFailureReason());

        try {
            if (orderGateway.reportPaymentResult(payment.getOrderId(), request)) {
                paymentWriter.markOrderNotified(payment.getId());
                log.info("Payment {} acknowledged by order {}", payment.getId(), payment.getOrderId());
            }
        } catch (RuntimeException ex) {
            // order-service answered but refused (order already cancelled, for instance). Retrying will
            // not change that answer, so stop tracking it and leave the record for reconciliation.
            log.warn("order-service rejected the verdict for payment {}: {}", payment.getId(), ex.getMessage());
            paymentWriter.markOrderNotified(payment.getId());
        }
    }
}
