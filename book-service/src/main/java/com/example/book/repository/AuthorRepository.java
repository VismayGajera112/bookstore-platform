package com.example.book.repository;

import com.example.book.entity.Author;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AuthorRepository extends JpaRepository<Author, Long> {

    Optional<Author> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    /**
     * The N+1 version: one SELECT for the page of authors, then one more SELECT per author the
     * moment anything touches {@code author.books}. Kept deliberately, to demonstrate the problem.
     */
    @Query("SELECT a FROM Author a ORDER BY a.id")
    List<Author> findPageOfAuthors(Pageable pageable);

    /**
     * Ids only — the first half of the two-step pattern that makes paging and collection fetching
     * coexist. Paging must happen before the join, or the join's duplicated rows corrupt the page
     * boundaries and Hibernate falls back to paginating in memory.
     */
    @Query("SELECT a.id FROM Author a ORDER BY a.id")
    List<Long> findAuthorIds(Pageable pageable);

    /**
     * The fix: one SELECT with a LEFT JOIN FETCH, so every collection arrives populated.
     * DISTINCT collapses the row duplication the join produces for multi-book authors.
     */
    @Query("SELECT DISTINCT a FROM Author a LEFT JOIN FETCH a.books WHERE a.id IN :ids ORDER BY a.id")
    List<Author> findAllWithBooksJoinFetch(@Param("ids") List<Long> ids);

    /** Same effect declaratively — Spring Data derives the fetch join from the entity graph. */
    @EntityGraph(attributePaths = "books")
    @Query("SELECT a FROM Author a WHERE a.id IN :ids ORDER BY a.id")
    List<Author> findAllWithBooksEntityGraph(@Param("ids") List<Long> ids);
}
