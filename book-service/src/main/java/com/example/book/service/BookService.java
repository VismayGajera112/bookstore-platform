package com.example.book.service;

import com.example.book.dto.BookAvailability;
import com.example.book.dto.BookRequest;
import com.example.book.dto.BookResponse;
import com.example.book.dto.StockReservationRequest;
import com.example.book.dto.StockReservationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface BookService {

    Page<BookResponse> findAll(String keyword, Pageable pageable);

    BookResponse findById(Long id);

    List<BookAvailability> findAvailability(List<Long> ids);

    BookResponse create(BookRequest request);

    BookResponse update(Long id, BookRequest request);

    void delete(Long id);

    StockReservationResponse reserveStock(StockReservationRequest request);

    StockReservationResponse releaseStock(Long orderId);
}
