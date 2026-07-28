package com.example.book.controller;

import com.example.book.dto.AuthorRequest;
import com.example.book.dto.AuthorResponse;
import com.example.book.dto.AuthorWithBooksResponse;
import com.example.book.service.AuthorService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/authors")
public class AuthorController {

    private final AuthorService authorService;

    public AuthorController(AuthorService authorService) {
        this.authorService = authorService;
    }

    /** PUBLIC. */
    @GetMapping
    public Page<AuthorResponse> list(@PageableDefault(size = 20, sort = "id",
            direction = Sort.Direction.ASC) Pageable pageable) {
        return authorService.findAll(pageable);
    }

    /** PUBLIC. */
    @GetMapping("/{id}")
    public AuthorResponse getOne(@PathVariable Long id) {
        return authorService.findById(id);
    }

    /**
     * PUBLIC — {@code ?naive=true} keeps the N+1 version reachable so the SQL log difference stays
     * demonstrable side by side with the fetch-joined default.
     */
    @GetMapping("/with-books")
    public List<AuthorWithBooksResponse> listWithBooks(
            @RequestParam(defaultValue = "false") boolean naive,
            @PageableDefault(size = 20) Pageable pageable) {
        return authorService.findAllWithBooks(naive, pageable);
    }

    /** ADMIN. */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthorResponse> create(@Valid @RequestBody AuthorRequest request) {
        AuthorResponse created = authorService.create(request);
        return ResponseEntity.created(URI.create("/api/authors/" + created.id())).body(created);
    }
}
