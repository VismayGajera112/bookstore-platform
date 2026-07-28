package com.example.payment.service;

import com.example.common.exception.BusinessRuleException;
import com.example.common.exception.ResourceNotFoundException;
import com.example.common.security.AuthenticatedUser;
import com.example.common.security.CurrentUser;
import com.example.payment.client.OrderGateway;
import com.example.payment.client.dto.OrderView;
import com.example.payment.client.dto.PaymentResultRequest;
import com.example.payment.dto.PaymentRequest;
import com.example.payment.dto.PaymentResponse;
import com.example.payment.entity.Payment;
import com.example.payment.event.PaymentEventPublisher;
import com.example.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);
    private static final String AWAITING_PAYMENT = "AWAITING_PAYMENT";

    private final PaymentRepository paymentRepository;
    private final PaymentWriter paymentWriter;
    private final OrderGateway orderGateway;
    private final PaymentEventPublisher eventPublisher;

    public PaymentServiceImpl(PaymentRepository paymentRepository,
                              PaymentWriter paymentWriter,
                              OrderGateway orderGateway,
                              PaymentEventPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.paymentWriter = paymentWriter;
        this.orderGateway = orderGateway;
        this.eventPublisher = eventPublisher;
    }

    /**
     * The payment leg of the saga:
     * <ol>
     *   <li>read the order from order-service, which also enforces that the caller owns it</li>
     *   <li>commit the verdict locally — before telling anyone, so a crash cannot lose a charge</li>
     *   <li>report it to order-service, which moves the order to PAID or compensates the reservation</li>
     * </ol>
     *
     * <p>The order of 2 and 3 is the important part. Charging first and recording afterwards can take
     * money without a record; recording first can at worst leave a verdict that has not been delivered,
     * and that is recoverable — {@link PaymentResultRedeliveryJob} keeps trying.
     */
    @Override
    public PaymentResponse pay(PaymentRequest request) {
        AuthenticatedUser caller = CurrentUser.require();

        var existing = paymentRepository.findByOrderId(request.orderId());
        if (existing.isPresent()) {
            // Unique on order_id: a resubmitted payment reports the first verdict instead of charging twice.
            Payment payment = existing.get();
            if (!caller.isAdmin() && !payment.getUserId().equals(caller.userId())) {
                throw new AccessDeniedException("Payment for order %d belongs to another user"
                        .formatted(request.orderId()));
            }
            throw new BusinessRuleException("Order %d has already been paid for (payment %d, %s)"
                    .formatted(payment.getOrderId(), payment.getId(), payment.getStatus()));
        }

        OrderView order = orderGateway.getOrder(request.orderId());
        if (!caller.isAdmin() && !order.userId().equals(caller.userId())) {
            throw new AccessDeniedException("Order %d belongs to another user".formatted(request.orderId()));
        }
        if (!AWAITING_PAYMENT.equals(order.status())) {
            throw new BusinessRuleException("Order %d is %s and is not awaiting payment"
                    .formatted(order.id(), order.status()));
        }

        // Stands in for the gateway call. A real integration would authorize here and store its reference.
        boolean approved = !request.simulateFailure();
        String failureReason = approved ? null : "Card declined by the payment provider";

        Payment payment = paymentWriter.record(Payment.builder()
                .orderId(order.id())
                .userId(order.userId())
                .amount(order.totalAmount())
                .status(approved ? Payment.Status.SUCCESS : Payment.Status.FAILED)
                .cardLast4(request.cardLast4())
                .failureReason(failureReason)
                .build());

        if (approved) {
            eventPublisher.paymentCompleted(payment);
        } else {
            eventPublisher.paymentFailed(payment);
        }

        PaymentResultRequest result = approved
                ? PaymentResultRequest.success(payment.getId())
                : PaymentResultRequest.failure(payment.getId(), failureReason);

        if (orderGateway.reportPaymentResult(order.id(), result)) {
            payment = paymentWriter.markOrderNotified(payment.getId());
        } else {
            log.warn("Payment {} recorded but order {} not yet updated; redelivery will retry",
                    payment.getId(), order.id());
        }

        return PaymentResponse.from(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse findByOrderId(Long orderId) {
        AuthenticatedUser caller = CurrentUser.require();
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("No payment for order " + orderId));

        if (!caller.isAdmin() && !payment.getUserId().equals(caller.userId())) {
            throw new AccessDeniedException("Payment for order %d belongs to another user".formatted(orderId));
        }
        return PaymentResponse.from(payment);
    }
}
