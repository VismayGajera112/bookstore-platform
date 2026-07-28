package com.example.order.client;

import com.example.order.client.dto.BookAvailability;
import com.example.order.client.dto.StockReservationRequest;
import com.example.order.client.dto.StockReservationResponse;
import com.example.order.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * The declarative HTTP contract with book-service.
 *
 * <p>Timeouts come from {@code spring.cloud.openfeign.client.config.book-service} and are explicit on
 * purpose: a dependency that hangs is worse than one that fails, because every waiting request also
 * pins a thread in this service.
 *
 * <p>The URL is configuration, not a hardcoded host — Step 8 can swap it for service discovery
 * without touching this interface.
 */
@FeignClient(name = "book-service", url = "${bookstore.clients.book-service.url}",
        configuration = FeignClientConfig.class)
public interface BookClient {

    @GetMapping("/api/books/availability")
    List<BookAvailability> getAvailability(@RequestParam("ids") List<Long> ids);

    @PostMapping("/api/books/reservations")
    StockReservationResponse reserveStock(@RequestBody StockReservationRequest request);

    @PostMapping("/api/books/reservations/{orderId}/release")
    StockReservationResponse releaseStock(@PathVariable("orderId") Long orderId);
}
