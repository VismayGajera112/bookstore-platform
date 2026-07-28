package com.example.book.controller;

import com.example.book.dto.BookAvailability;
import com.example.book.dto.BookRequest;
import com.example.book.dto.BookResponse;
import com.example.book.dto.StockReservationRequest;
import com.example.book.dto.StockReservationResponse;
import com.example.book.service.BookService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    /** PUBLIC — browsing and searching the catalog needs no account. */
    @GetMapping
    public Page<BookResponse> list(@RequestParam(required = false) String keyword,
                                   @PageableDefault(size = 20, sort = "id",
                                           direction = Sort.Direction.ASC) Pageable pageable) {
        return bookService.findAll(keyword, pageable);
    }

    /** PUBLIC. */
    @GetMapping("/{id}")
    public BookResponse getOne(@PathVariable Long id) {
        return bookService.findById(id);
    }

    /**
     * Batch price/stock lookup for other services. Requires a valid token: order-service forwards the
     * customer's JWT, so this call carries the same identity as the request that triggered it.
     */
    @GetMapping("/availability")
    @PreAuthorize("isAuthenticated()")
    public List<BookAvailability> availability(@RequestParam List<Long> ids) {
        return bookService.findAvailability(ids);
    }

    /** ADMIN — catalog writes. */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BookResponse> create(@Valid @RequestBody BookRequest request) {
        BookResponse created = bookService.create(request);
        return ResponseEntity.created(URI.create("/api/books/" + created.id())).body(created);
    }

    /** ADMIN. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public BookResponse update(@PathVariable Long id, @Valid @RequestBody BookRequest request) {
        return bookService.update(id, request);
    }

    /** ADMIN. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        bookService.delete(id);
    }

    /** USER — called by order-service while placing an order; decrements stock atomically. */
    @PostMapping("/reservations")
    @PreAuthorize("isAuthenticated()")
    public StockReservationResponse reserve(@Valid @RequestBody StockReservationRequest request) {
        return bookService.reserveStock(request);
    }

    /** USER — the saga's compensating call: returns reserved units after a cancel or failed payment. */
    @PostMapping("/reservations/{orderId}/release")
    @PreAuthorize("isAuthenticated()")
    public StockReservationResponse release(@PathVariable Long orderId) {
        return bookService.releaseStock(orderId);
    }
}
