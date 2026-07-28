package com.example.order.controller;

import com.example.order.dto.OrderResponse;
import com.example.order.dto.PaymentResultRequest;
import com.example.order.dto.PlaceOrderRequest;
import com.example.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** USER — checks stock via book-service, then creates the order. */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrderResponse> place(@Valid @RequestBody PlaceOrderRequest request) {
        OrderResponse created = orderService.placeOrder(request);
        return ResponseEntity.created(URI.create("/api/orders/" + created.id())).body(created);
    }

    /** ADMIN — every order, mapped before /{id} so "all" is not read as an id. */
    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public Page<OrderResponse> listAll(@PageableDefault(size = 20, sort = "id",
            direction = Sort.Direction.DESC) Pageable pageable) {
        return orderService.findAllOrders(pageable);
    }

    /** USER — the caller's own orders. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Page<OrderResponse> listMine(@PageableDefault(size = 20, sort = "id",
            direction = Sort.Direction.DESC) Pageable pageable) {
        return orderService.findMyOrders(pageable);
    }

    /** USER (own order) or ADMIN — ownership is enforced in the service, from the token's uid claim. */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public OrderResponse getOne(@PathVariable Long id) {
        return orderService.findById(id);
    }

    /** USER (own order) or ADMIN — allowed until the order has shipped. */
    @PutMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public OrderResponse cancel(@PathVariable Long id) {
        return orderService.cancel(id);
    }

    /**
     * The saga callback from payment-service, carrying the customer's forwarded token so the same
     * owner-or-admin rule applies. Step 7 replaces it with a {@code PaymentCompleted} event.
     */
    @PutMapping("/{id}/payment-result")
    @PreAuthorize("isAuthenticated()")
    public OrderResponse applyPaymentResult(@PathVariable Long id,
                                            @Valid @RequestBody PaymentResultRequest request) {
        return orderService.applyPaymentResult(id, request);
    }
}
