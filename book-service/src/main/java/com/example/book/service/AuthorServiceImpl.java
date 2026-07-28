package com.example.book.service;

import com.example.book.dto.AuthorRequest;
import com.example.book.dto.AuthorResponse;
import com.example.book.dto.AuthorWithBooksResponse;
import com.example.book.entity.Author;
import com.example.book.repository.AuthorRepository;
import com.example.common.exception.DuplicateResourceException;
import com.example.common.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class AuthorServiceImpl implements AuthorService {

    private final AuthorRepository authorRepository;

    public AuthorServiceImpl(AuthorRepository authorRepository) {
        this.authorRepository = authorRepository;
    }

    @Override
    public Page<AuthorResponse> findAll(Pageable pageable) {
        return authorRepository.findAll(pageable).map(AuthorResponse::from);
    }

    @Override
    public AuthorResponse findById(Long id) {
        return AuthorResponse.from(authorRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Author", id)));
    }

    @Override
    @Transactional
    public AuthorResponse create(AuthorRequest request) {
        String name = request.name().trim();
        if (authorRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicateResourceException("An author named %s already exists".formatted(name));
        }
        return AuthorResponse.from(authorRepository.save(Author.builder().name(name).build()));
    }

    @Override
    public List<AuthorWithBooksResponse> findAllWithBooks(boolean naive, Pageable pageable) {
        List<Author> authors = naive
                ? authorRepository.findPageOfAuthors(pageable)
                : authorRepository.findAllWithBooksJoinFetch(authorRepository.findAuthorIds(pageable));

        return authors.stream()
                .map(AuthorWithBooksResponse::from)
                .toList();
    }
}
