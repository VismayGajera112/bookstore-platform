package com.example.book.dto;

import com.example.book.entity.Author;

import java.time.Instant;

public record AuthorResponse(Long id, String name, Instant createdAt) {

    public static AuthorResponse from(Author author) {
        return new AuthorResponse(author.getId(), author.getName(), author.getCreatedAt());
    }
}
