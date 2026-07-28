package com.example.order.service;

import com.example.order.dto.OrderResponse;
import com.example.order.dto.PaymentResultRequest;
import com.example.order.dto.PlaceOrderRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {

    OrderResponse placeOrder(PlaceOrderRequest request);

    OrderResponse findById(Long id);

    Page<OrderResponse> findMyOrders(Pageable pageable);

    Page<OrderResponse> findAllOrders(Pageable pageable);

    OrderResponse cancel(Long id);

    OrderResponse applyPaymentResult(Long orderId, PaymentResultRequest result);
}
