package com.example.book.service;

import com.example.book.config.AwsProperties;
import com.example.book.dto.BrowsingHistoryItem;
import com.example.book.entity.Book;
import com.example.book.repository.BookRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * User browsing history in DynamoDB.
 *
 * <p>Partition key {@code userId} spreads load across users (no hot partition from a single book).
 * Sort key {@code viewedAt} (ISO-8601) lets us query newest-first with {@code ScanIndexForward=false}.
 * TTL attribute {@code expiresAt} auto-deletes entries older than the configured window (default 30 days).
 */
@Service
public class BrowsingHistoryService {

    private static final Logger log = LoggerFactory.getLogger(BrowsingHistoryService.class);

    private final DynamoDbClient dynamoDb;
    private final BookRepository bookRepository;
    private final AwsProperties awsProperties;

    public BrowsingHistoryService(DynamoDbClient dynamoDb,
                                  BookRepository bookRepository,
                                  AwsProperties awsProperties) {
        this.dynamoDb = dynamoDb;
        this.bookRepository = bookRepository;
        this.awsProperties = awsProperties;
    }

    /**
     * Fire-and-forget write so catalog reads stay fast. Caller must pass {@code userId} explicitly —
     * {@code SecurityContext} is thread-local and does not follow {@code @Async} workers.
     */
    @Async
    public void recordViewAsync(Long userId, Long bookId) {
        try {
            recordView(userId, bookId);
        } catch (Exception ex) {
            // History must never fail the catalog read path.
            log.warn("Failed to record browsing history for userId={} bookId={}: {}",
                    userId, bookId, ex.getMessage());
        }
    }

    public void recordView(Long userId, Long bookId) {
        Instant viewedAt = Instant.now();
        Instant expiresAt = viewedAt.plus(awsProperties.dynamodb().browsingHistoryTtlDays(), ChronoUnit.DAYS);

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("userId", AttributeValue.builder().s(Long.toString(userId)).build());
        // Sort key includes bookId so two views in the same millisecond for different books don't collide.
        item.put("viewedAt", AttributeValue.builder().s(viewedAt.toString() + "#" + bookId).build());
        item.put("bookId", AttributeValue.builder().n(Long.toString(bookId)).build());
        item.put("viewedAtIso", AttributeValue.builder().s(viewedAt.toString()).build());
        item.put("expiresAt", AttributeValue.builder().n(Long.toString(expiresAt.getEpochSecond())).build());

        dynamoDb.putItem(PutItemRequest.builder()
                .tableName(awsProperties.dynamodb().browsingHistoryTable())
                .item(item)
                .build());

        log.debug("Recorded view userId={} bookId={}", userId, bookId);
    }

    public List<BrowsingHistoryItem> recentForUser(Long userId, int limit) {
        int capped = Math.min(Math.max(limit, 1), 50);

        var response = dynamoDb.query(QueryRequest.builder()
                .tableName(awsProperties.dynamodb().browsingHistoryTable())
                .keyConditionExpression("userId = :uid")
                .expressionAttributeValues(Map.of(
                        ":uid", AttributeValue.builder().s(Long.toString(userId)).build()))
                .scanIndexForward(false)
                .limit(capped)
                .build());

        List<Long> bookIds = response.items().stream()
                .map(item -> Long.parseLong(item.get("bookId").n()))
                .distinct()
                .toList();

        Map<Long, Book> booksById = bookRepository.findAllByIdIn(bookIds).stream()
                .collect(Collectors.toMap(Book::getId, Function.identity()));

        return response.items().stream()
                .map(item -> {
                    Long bookId = Long.parseLong(item.get("bookId").n());
                    Book book = booksById.get(bookId);
                    Instant viewedAt = Instant.parse(
                            item.containsKey("viewedAtIso")
                                    ? item.get("viewedAtIso").s()
                                    : item.get("viewedAt").s().split("#")[0]);
                    return new BrowsingHistoryItem(
                            bookId,
                            book != null ? book.getTitle() : null,
                            book != null && book.getAuthor() != null ? book.getAuthor().getName() : null,
                            book != null ? book.getCoverUrl() : null,
                            viewedAt);
                })
                .toList();
    }
}
