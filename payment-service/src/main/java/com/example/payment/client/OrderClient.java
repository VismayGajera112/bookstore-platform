package com.example.payment.client;

import com.example.payment.client.dto.OrderView;
import com.example.payment.client.dto.PaymentResultRequest;
import com.example.payment.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "order-service", url = "${bookstore.clients.order-service.url}",
        configuration = FeignClientConfig.class)
public interface OrderClient {

    /**
     * Fetches the order being paid for. Because the customer's JWT is forwarded, order-service applies
     * its own owner-or-admin rule — payment-service does not have to re-implement that check, and
     * cannot be tricked into paying for someone else's order.
     */
    @GetMapping("/api/orders/{id}")
    OrderView getOrder(@PathVariable("id") Long id);

    @PutMapping("/api/orders/{id}/payment-result")
    OrderView reportPaymentResult(@PathVariable("id") Long id, @RequestBody PaymentResultRequest request);
}
