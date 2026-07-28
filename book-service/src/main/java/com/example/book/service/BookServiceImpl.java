package com.example.book.service;

import com.example.book.dto.BookAvailability;
import com.example.book.dto.BookRequest;
import com.example.book.dto.BookResponse;
import com.example.book.dto.StockReservationRequest;
import com.example.book.dto.StockReservationResponse;
import com.example.book.entity.Author;
import com.example.book.entity.Book;
import com.example.book.entity.StockReservation;
import com.example.book.repository.AuthorRepository;
import com.example.book.repository.BookRepository;
import com.example.book.repository.StockReservationRepository;
import com.example.common.exception.BusinessRuleException;
import com.example.common.exception.DuplicateResourceException;
import com.example.common.exception.InsufficientStockException;
import com.example.common.exception.ResourceNotFoundException;
import com.example.common.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class BookServiceImpl implements BookService {

    private static final Logger log = LoggerFactory.getLogger(BookServiceImpl.class);

    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;
    private final StockReservationRepository reservationRepository;
    private final BrowsingHistoryService browsingHistoryService;

    public BookServiceImpl(BookRepository bookRepository,
                           AuthorRepository authorRepository,
                           StockReservationRepository reservationRepository,
                           BrowsingHistoryService browsingHistoryService) {
        this.bookRepository = bookRepository;
        this.authorRepository = authorRepository;
        this.reservationRepository = reservationRepository;
        this.browsingHistoryService = browsingHistoryService;
    }

    @Override
    public Page<BookResponse> findAll(String keyword, Pageable pageable) {
        Page<Book> page = StringUtils.hasText(keyword)
                ? bookRepository.search(keyword.trim(), pageable)
                : bookRepository.findAllWithAuthor(pageable);
        return page.map(BookResponse::from);
    }

    @Override
    public BookResponse findById(Long id) {
        BookResponse response = BookResponse.from(bookRepository.findByIdWithAuthor(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Book", id)));
        // Async DynamoDB write — does not block the catalog response.
        CurrentUser.get().ifPresent(user -> browsingHistoryService.recordViewAsync(user.userId(), id));
        return response;
    }

    /**
     * Batch lookup: order-service prices a whole basket in one call rather than one request per line,
     * which keeps a network round trip per item from turning into a distributed N+1.
     */
    @Override
    public List<BookAvailability> findAvailability(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Book> books = bookRepository.findAllByIdIn(ids);
        Map<Long, Book> booksById = books.stream().collect(Collectors.toMap(Book::getId, Function.identity()));

        List<Long> missing = ids.stream().distinct().filter(id -> !booksById.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            throw new ResourceNotFoundException("Books not found: " + missing);
        }
        return ids.stream().distinct().map(id -> BookAvailability.from(booksById.get(id))).toList();
    }

    @Override
    @Transactional
    public BookResponse create(BookRequest request) {
        if (StringUtils.hasText(request.isbn()) && bookRepository.existsByIsbn(request.isbn())) {
            throw new DuplicateResourceException("A book with isbn %s already exists".formatted(request.isbn()));
        }

        Book book = Book.builder()
                .title(request.title())
                .author(requireAuthor(request.authorId()))
                .isbn(request.isbn())
                .price(request.price())
                .stock(request.stock())
                .coverUrl(request.coverUrl())
                .build();

        return BookResponse.from(bookRepository.save(book));
    }

    @Override
    @Transactional
    public BookResponse update(Long id, BookRequest request) {
        Book book = bookRepository.findByIdWithAuthor(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Book", id));

        if (StringUtils.hasText(request.isbn()) && bookRepository.existsByIsbnAndIdNot(request.isbn(), id)) {
            throw new DuplicateResourceException("A book with isbn %s already exists".formatted(request.isbn()));
        }

        book.setTitle(request.title());
        book.setAuthor(requireAuthor(request.authorId()));
        book.setIsbn(request.isbn());
        book.setPrice(request.price());
        book.setStock(request.stock());
        book.setCoverUrl(request.coverUrl());

        return BookResponse.from(bookRepository.save(book));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Book", id));
        bookRepository.delete(book);
    }

    /**
     * One local transaction covers the whole reservation: every book is loaded, checked, decremented
     * and recorded together, so a shortfall on the last line rolls back the earlier decrements.
     * Concurrent reservations of the same book collide on the {@code @Version} column and the loser
     * gets an optimistic-lock failure (HTTP 409) instead of overselling.
     *
     * <p>Within book-service this is still ACID. What it is not is atomic with the order row in
     * order_db — that gap is what the saga's compensating release exists to close.
     */
    @Override
    @Transactional
    public StockReservationResponse reserveStock(StockReservationRequest request) {
        var existing = reservationRepository.findByOrderIdWithLines(request.orderId());
        if (existing.isPresent()) {
            StockReservation reservation = existing.get();
            if (reservation.getStatus() == StockReservation.Status.RELEASED) {
                throw new BusinessRuleException(
                        "Stock for order %d was already released and cannot be reserved again"
                                .formatted(request.orderId()));
            }
            // A retry of a call that already succeeded: report the same outcome, change nothing.
            log.info("Reservation for order {} already exists; returning it unchanged", request.orderId());
            return describe(reservation);
        }

        Map<Long, Integer> quantityByBookId = new LinkedHashMap<>();
        request.items().forEach(item -> quantityByBookId.merge(item.bookId(), item.quantity(), Integer::sum));

        Map<Long, Book> booksById = bookRepository.findAllByIdIn(List.copyOf(quantityByBookId.keySet())).stream()
                .collect(Collectors.toMap(Book::getId, Function.identity()));

        List<StockReservation.Line> lines = new ArrayList<>();
        quantityByBookId.forEach((bookId, quantity) -> {
            Book book = booksById.get(bookId);
            if (book == null) {
                throw ResourceNotFoundException.of("Book", bookId);
            }
            try {
                book.reduceStock(quantity);
            } catch (IllegalStateException ex) {
                throw new InsufficientStockException(ex.getMessage());
            }
            lines.add(new StockReservation.Line(bookId, quantity));
        });

        bookRepository.saveAll(booksById.values());
        StockReservation reservation = reservationRepository.save(StockReservation.builder()
                .orderId(request.orderId())
                .status(StockReservation.Status.RESERVED)
                .lines(lines)
                .build());

        log.info("Reserved stock for order {}: {} line(s)", request.orderId(), lines.size());
        return describe(reservation);
    }

    /**
     * The compensating action of the saga. Quantities come from book-service's own reservation record,
     * so a caller cannot inflate stock by resending different lines, and releasing twice is a no-op.
     */
    @Override
    @Transactional
    public StockReservationResponse releaseStock(Long orderId) {
        StockReservation reservation = reservationRepository.findByOrderIdWithLines(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("No stock reservation for order " + orderId));

        if (reservation.getStatus() == StockReservation.Status.RELEASED) {
            log.info("Reservation for order {} is already released; nothing to do", orderId);
            return describe(reservation);
        }

        Map<Long, Book> booksById = bookRepository.findAllByIdIn(
                        reservation.getLines().stream().map(StockReservation.Line::getBookId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Book::getId, Function.identity()));

        reservation.getLines().forEach(line -> {
            Book book = booksById.get(line.getBookId());
            if (book == null) {
                // The book was deleted after reservation; there is nothing to give back to.
                log.warn("Cannot return {} unit(s) of deleted book {} from order {}",
                        line.getQuantity(), line.getBookId(), orderId);
                return;
            }
            book.increaseStock(line.getQuantity());
        });

        bookRepository.saveAll(booksById.values());
        reservation.setStatus(StockReservation.Status.RELEASED);
        reservation.setReleasedAt(Instant.now());

        log.info("Released stock for order {}", orderId);
        return describe(reservationRepository.save(reservation));
    }

    private StockReservationResponse describe(StockReservation reservation) {
        Map<Long, Book> booksById = bookRepository.findAllByIdIn(
                        reservation.getLines().stream().map(StockReservation.Line::getBookId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Book::getId, Function.identity()));

        List<StockReservationResponse.Line> lines = reservation.getLines().stream()
                .map(line -> new StockReservationResponse.Line(
                        line.getBookId(),
                        line.getQuantity(),
                        booksById.containsKey(line.getBookId())
                                ? booksById.get(line.getBookId()).getStock()
                                : null))
                .toList();

        return StockReservationResponse.from(reservation, lines);
    }

    private Author requireAuthor(Long authorId) {
        return authorRepository.findById(authorId)
                .orElseThrow(() -> ResourceNotFoundException.of("Author", authorId));
    }
}
