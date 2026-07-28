package com.example.payment.service;

import com.example.common.exception.ResourceNotFoundException;
import com.example.payment.entity.Payment;
import com.example.payment.repository.PaymentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Local commits, separated from the orchestration for the same reason as in order-service: the verdict
 * must be durable before the remote callback is attempted, so no database connection is held open across
 * an HTTP call.
 */
@Component
public class PaymentWriter {

    private final PaymentRepository paymentRepository;

    public PaymentWriter(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment record(Payment payment) {
        return paymentRepository.save(payment);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment markOrderNotified(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Payment", paymentId));
        payment.setOrderNotified(true);
        return paymentRepository.save(payment);
    }
}
