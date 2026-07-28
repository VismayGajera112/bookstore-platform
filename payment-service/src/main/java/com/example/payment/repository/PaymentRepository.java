package com.example.payment.repository;

import com.example.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(Long orderId);

    /** Verdicts order-service has not acknowledged yet; the sweeper redelivers these. */
    List<Payment> findByOrderNotifiedFalse();
}
