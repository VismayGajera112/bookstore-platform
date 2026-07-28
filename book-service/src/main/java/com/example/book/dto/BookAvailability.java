package com.example.book.dto;

import com.example.book.entity.Book;

import java.math.BigDecimal;

/**
 * The narrow projection order-service needs to price an order and check availability. Keeping it
 * separate from {@link BookResponse} means the catalog can add public fields without changing the
 * contract another service depends on.
 */
public record BookAvailability(Long id, String title, BigDecimal price, Integer stock) {

    public static BookAvailability from(Book book) {
        return new BookAvailability(book.getId(), book.getTitle(), book.getPrice(), book.getStock());
    }
}
