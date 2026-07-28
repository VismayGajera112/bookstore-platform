package com.example.book.dto;

import com.example.book.entity.Book;

import java.math.BigDecimal;
import java.time.Instant;

public record BookResponse(
        Long id,
        String title,
        Long authorId,
        String authorName,
        String isbn,
        BigDecimal price,
        Integer stock,
        String coverUrl,
        Instant createdAt
) {

    public static BookResponse from(Book book) {
        return new BookResponse(
                book.getId(),
                book.getTitle(),
                book.getAuthor().getId(),
                book.getAuthor().getName(),
                book.getIsbn(),
                book.getPrice(),
                book.getStock(),
                book.getCoverUrl(),
                book.getCreatedAt());
    }
}
