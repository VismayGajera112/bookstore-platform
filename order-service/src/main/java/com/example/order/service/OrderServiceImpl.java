package com.example.order.service;

import com.example.common.exception.BusinessRuleException;
import com.example.common.exception.InsufficientStockException;
import com.example.common.exception.ResourceNotFoundException;
import com.example.common.exception.ServiceUnavailableException;
import com.example.common.security.AuthenticatedUser;
import com.example.common.security.CurrentUser;
import com.example.order.client.CatalogGateway;
import com.example.order.client.dto.BookAvailability;
import com.example.order.client.dto.StockReservationRequest;
import com.example.order.dto.OrderResponse;
import com.example.order.dto.PaymentResultRequest;
import com.example.order.dto.PlaceOrderRequest;
import com.example.order.entity.Order;
import com.example.order.entity.OrderItem;
import com.example.order.entity.OrderStatus;
import com.example.order.event.OrderEventPublisher;
import com.example.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orchestrates the order saga. Placing an order touches two services and two databases, so there is no
 * transaction that can cover it; instead each step commits locally and every step that can fail has a
 * defined answer to "what undoes the step before it".
 *
 * <pre>
 *   1. ask book-service for price and stock        (read-only, no compensation needed)
 *   2. commit the order as PENDING                 (local)
 *   3. ask book-service to reserve stock           (remote, idempotent on orderId)
 *   4. commit AWAITING_PAYMENT                     (local)
 *   ── later ──
 *   5. payment-service reports its verdict
 *      SUCCESS → commit PAID
 *      FAILURE → release stock (compensate step 3), commit CANCELLED
 * </pre>
 *
 * <p>Step 2 before step 3 is not an accident: the reservation needs an order id to be idempotent, and
 * a crash between them leaves a PENDING order holding nothing — visible, harmless, and easy to reap.
 * The reverse order could leave stock reserved for an order that never existed.
 */
@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderRepository orderRepository;
    private final OrderStateWriter stateWriter;
    private final CatalogGateway catalogGateway;
    private final OrderEventPublisher eventPublisher;

    public OrderServiceImpl(OrderRepository orderRepository,
                            OrderStateWriter stateWriter,
                            CatalogGateway catalogGateway,
                            OrderEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.stateWriter = stateWriter;
        this.catalogGateway = catalogGateway;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Note the absence of {@code @Transactional} on this method. Wrapping the whole saga in one
     * transaction would hold a database connection open across two HTTP calls and would still not make
     * the remote work atomic — it would only add a long-lived lock to the failure modes.
     */
    @Override
    public OrderResponse placeOrder(PlaceOrderRequest request) {
        AuthenticatedUser caller = CurrentUser.require();

        Map<Long, Integer> quantityByBookId = new LinkedHashMap<>();
        request.items().forEach(item -> quantityByBookId.merge(item.bookId(), item.quantity(), Integer::sum));

        // The catalog owns price and stock, so both come from book-service and never from the request.
        List<BookAvailability> catalog = catalogGateway.getAvailability(List.copyOf(quantityByBookId.keySet()));
        Map<Long, BookAvailability> byId = catalog.stream()
                .collect(Collectors.toMap(BookAvailability::id, Function.identity()));

        List<OrderItem> items = quantityByBookId.entrySet().stream()
                .map(entry -> toOrderItem(byId, entry.getKey(), entry.getValue()))
                .toList();

        Order order = stateWriter.createPendingOrder(caller.userId(), caller.username(), items);
        log.info("Order {} created as PENDING for user {}", order.getId(), caller.userId());

        return OrderResponse.from(reserveOrUnwind(order));
    }

    private Order reserveOrUnwind(Order order) {
        StockReservationRequest reservation = new StockReservationRequest(
                order.getId(),
                order.getItems().stream()
                        .map(item -> new StockReservationRequest.Item(item.getBookId(), item.getQuantity()))
                        .toList());

        try {
            catalogGateway.reserveStock(reservation);
        } catch (ServiceUnavailableException ex) {
            // No stock was taken, so nothing needs releasing; the PENDING order is closed off and the
            // customer gets a retryable 503 rather than a request that hangs on book-service.
            stateWriter.markCancelled(order.getId(), "Catalog unavailable while reserving stock", false);
            throw ex;
        } catch (InsufficientStockException | BusinessRuleException | ResourceNotFoundException ex) {
            stateWriter.markCancelled(order.getId(), ex.getMessage(), false);
            throw ex;
        }

        Order reserved = stateWriter.markStockReserved(order.getId());
        eventPublisher.orderCreated(reserved);
        return reserved;
    }

    private OrderItem toOrderItem(Map<Long, BookAvailability> catalogById, Long bookId, Integer quantity) {
        BookAvailability book = catalogById.get(bookId);
        if (book == null) {
            throw ResourceNotFoundException.of("Book", bookId);
        }
        if (book.stock() < quantity) {
            // book-service checks this again under its own transaction; failing here just avoids
            // creating an order that is certain to be rejected a moment later.
            throw new InsufficientStockException(
                    "Insufficient stock for '%s': requested %d, available %d"
                            .formatted(book.title(), quantity, book.stock()));
        }
        return OrderItem.builder()
                .bookId(bookId)
                .bookTitle(book.title())
                .quantity(quantity)
                .unitPrice(book.price())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse findById(Long id) {
        Order order = orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", id));
        requireOwnerOrAdmin(order);
        return OrderResponse.from(order);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> findMyOrders(Pageable pageable) {
        return orderRepository.findByUserIdWithItems(CurrentUser.require().userId(), pageable)
                .map(OrderResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> findAllOrders(Pageable pageable) {
        return orderRepository.findAllWithItems(pageable).map(OrderResponse::from);
    }

    /**
     * Cancelling is a customer-facing action, so it must not depend on book-service being up. If the
     * compensating release cannot be delivered the order is still cancelled and the debt is recorded on
     * the row for {@link StockReleaseRetryJob} to settle — eventual consistency in the small.
     */
    @Override
    public OrderResponse cancel(Long id) {
        Order order = orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", id));
        requireOwnerOrAdmin(order);

        if (!order.getStatus().isCancellable()) {
            throw new BusinessRuleException(
                    "Order %d cannot be cancelled while it is %s".formatted(id, order.getStatus()));
        }

        boolean stockStillHeld = false;
        if (order.isStockReserved()) {
            stockStillHeld = !catalogGateway.releaseStock(id);
        }

        Order cancelled = stateWriter.markCancelled(id, "Cancelled by " + CurrentUser.require().username(),
                stockStillHeld);
        eventPublisher.orderCancelled(cancelled, cancelled.getStatusReason());
        return OrderResponse.from(cancelled);
    }

    @Override
    public OrderResponse applyPaymentResult(Long orderId, PaymentResultRequest result) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));
        requireOwnerOrAdmin(order);

        if (result.status() == PaymentResultRequest.Status.SUCCESS) {
            return OrderResponse.from(applySuccessfulPayment(order, result));
        }
        return OrderResponse.from(applyFailedPayment(order, result));
    }

    private Order applySuccessfulPayment(Order order, PaymentResultRequest result) {
        // Idempotent: a redelivered success for an order already marked PAID changes nothing.
        if (order.getStatus() == OrderStatus.PAID
                && result.paymentId().equals(order.getPaymentId())) {
            return order;
        }
        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
            throw new BusinessRuleException(
                    "Order %d is %s and cannot accept a payment".formatted(order.getId(), order.getStatus()));
        }

        Order paid = stateWriter.markPaid(order.getId(), result.paymentId());
        eventPublisher.orderPaid(paid);
        log.info("Order {} is PAID via payment {}", paid.getId(), result.paymentId());
        return paid;
    }

    private Order applyFailedPayment(Order order, PaymentResultRequest result) {
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return order;
        }

        boolean stockStillHeld = false;
        if (order.isStockReserved()) {
            stockStillHeld = !catalogGateway.releaseStock(order.getId());
        }

        String reason = "Payment failed: " + (result.reason() == null ? "declined" : result.reason());
        Order cancelled = stateWriter.markCancelled(order.getId(), reason, stockStillHeld);
        eventPublisher.orderCancelled(cancelled, reason);
        log.info("Order {} cancelled after failed payment; stock still held: {}",
                cancelled.getId(), stockStillHeld);
        return cancelled;
    }

    /**
     * order-service cannot ask user-service who owns what — it compares the order's stored
     * {@code userId} with the {@code uid} claim in the verified token, which is all it needs.
     */
    private void requireOwnerOrAdmin(Order order) {
        AuthenticatedUser caller = CurrentUser.require();
        if (caller.isAdmin() || order.getUserId().equals(caller.userId())) {
            return;
        }
        throw new AccessDeniedException("Order %d belongs to another user".formatted(order.getId()));
    }
}
