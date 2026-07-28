package com.example.book.service;

import com.example.book.dto.AuthorRequest;
import com.example.book.dto.AuthorResponse;
import com.example.book.dto.AuthorWithBooksResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AuthorService {

    Page<AuthorResponse> findAll(Pageable pageable);

    AuthorResponse findById(Long id);

    AuthorResponse create(AuthorRequest request);

    List<AuthorWithBooksResponse> findAllWithBooks(boolean naive, Pageable pageable);
}
