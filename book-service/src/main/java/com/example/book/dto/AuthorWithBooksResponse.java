package com.example.book.dto;

import com.example.book.entity.Author;

import java.util.List;

public record AuthorWithBooksResponse(Long id, String name, List<String> bookTitles) {

    public static AuthorWithBooksResponse from(Author author) {
        return new AuthorWithBooksResponse(
                author.getId(),
                author.getName(),
                author.getBooks().stream().map(book -> book.getTitle()).toList());
    }
}
