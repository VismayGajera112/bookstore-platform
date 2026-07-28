package com.example.book.repository;

import com.example.book.entity.Book;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {

    /**
     * Listing pulls the author in the same statement; without the fetch join, rendering the response
     * DTO would issue one extra SELECT per row (the classic N+1 on a to-one association).
     */
    @Query(value = "SELECT b FROM Book b JOIN FETCH b.author",
            countQuery = "SELECT count(b) FROM Book b")
    Page<Book> findAllWithAuthor(Pageable pageable);

    /**
     * Infix keyword search over title and author name. The lower(...) LIKE '%x%' shape matches the
     * trigram GIN indexes created in V1__catalog.sql, so this stays an index scan as the table grows.
     */
    @Query(value = """
            SELECT b FROM Book b JOIN FETCH b.author a
            WHERE lower(b.title) LIKE lower(CONCAT('%', :keyword, '%'))
               OR lower(a.name)  LIKE lower(CONCAT('%', :keyword, '%'))
            """,
            countQuery = """
                    SELECT count(b) FROM Book b JOIN b.author a
                    WHERE lower(b.title) LIKE lower(CONCAT('%', :keyword, '%'))
                       OR lower(a.name)  LIKE lower(CONCAT('%', :keyword, '%'))
                    """)
    Page<Book> search(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT b FROM Book b JOIN FETCH b.author WHERE b.id = :id")
    Optional<Book> findByIdWithAuthor(@Param("id") Long id);

    /** Loads every book of a reservation in one statement, ordered by id to keep lock order stable. */
    @Query("SELECT b FROM Book b JOIN FETCH b.author WHERE b.id IN :ids ORDER BY b.id")
    List<Book> findAllByIdIn(@Param("ids") List<Long> ids);

    boolean existsByIsbn(String isbn);

    boolean existsByIsbnAndIdNot(String isbn, Long id);
}
