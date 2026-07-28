package com.example.order.service;

import com.example.common.exception.ResourceNotFoundException;
import com.example.order.entity.Order;
import com.example.order.entity.OrderItem;
import com.example.order.entity.OrderStatus;
import com.example.order.repository.OrderRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Each saga step's local transaction, kept in its own bean for a reason that matters: a transaction
 * must commit <em>before</em> the next remote call, so the state survives if that call fails or this
 * service dies mid-sequence. An order id cannot be sent to book-service unless the order row is
 * already durable, and a reservation cannot be recorded unless the confirmation is durable too.
 *
 * <p>Spring's proxies only apply {@code @Transactional} to calls that arrive from outside the bean, so
 * these steps cannot live as private methods on the orchestrator — self-invocation would silently run
 * them all inside one long transaction, which is exactly what must not happen.
 */
@Component
public class OrderStateWriter {

    private final OrderRepository orderRepository;

    public OrderStateWriter(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order createPendingOrder(Long userId, String username, List<OrderItem> items) {
        Order order = Order.builder()
                .userId(userId)
                .username(username)
                .status(OrderStatus.PENDING)
                .totalAmount(java.math.BigDecimal.ZERO)
                .build();

        items.forEach(order::addItem);
        order.recalculateTotal();

        return orderRepository.save(order);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order markStockReserved(Long orderId) {
        Order order = require(orderId);
        order.setStatus(OrderStatus.AWAITING_PAYMENT);
        order.setStockReserved(true);
        order.setStatusReason(null);
        return orderRepository.save(order);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order markCancelled(Long orderId, String reason, boolean stockStillHeld) {
        Order order = require(orderId);
        order.setStatus(OrderStatus.CANCELLED);
        order.setStatusReason(reason);
        order.setStockReserved(stockStillHeld);
        order.setStockReleasePending(stockStillHeld);
        return orderRepository.save(order);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order markPaid(Long orderId, Long paymentId) {
        Order order = require(orderId);
        order.setStatus(OrderStatus.PAID);
        order.setPaymentId(paymentId);
        order.setStatusReason(null);
        // The reservation is now a sale: stock stays decremented in book-service and must not be
        // released by a later cancel or compensation sweep.
        order.setStockReserved(false);
        order.setStockReleasePending(false);
        return orderRepository.save(order);
    }

    /** Called once a deferred compensation finally lands. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order markStockReleased(Long orderId) {
        Order order = require(orderId);
        order.setStockReserved(false);
        order.setStockReleasePending(false);
        return orderRepository.save(order);
    }

    private Order require(Long orderId) {
        return orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));
    }
}
