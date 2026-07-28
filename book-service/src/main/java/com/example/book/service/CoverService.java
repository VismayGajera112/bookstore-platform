package com.example.book.service;

import com.example.book.config.AwsProperties;
import com.example.book.dto.CoverMetadataResponse;
import com.example.book.dto.CoverUploadResponse;
import com.example.book.entity.Book;
import com.example.book.repository.BookRepository;
import com.example.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
public class CoverService {

    private static final Logger log = LoggerFactory.getLogger(CoverService.class);

    private final BookRepository bookRepository;
    private final S3Presigner s3Presigner;
    private final DynamoDbClient dynamoDb;
    private final AwsProperties awsProperties;

    public CoverService(BookRepository bookRepository,
                        S3Presigner s3Presigner,
                        DynamoDbClient dynamoDb,
                        AwsProperties awsProperties) {
        this.bookRepository = bookRepository;
        this.s3Presigner = s3Presigner;
        this.dynamoDb = dynamoDb;
        this.awsProperties = awsProperties;
    }

    /**
     * Issues a short-lived S3 PUT URL. The admin client uploads bytes directly to S3; the
     * cover-processor Lambda then writes metadata and emails via SNS.
     */
    @Transactional
    public CoverUploadResponse createUploadUrl(Long bookId, String contentType) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> ResourceNotFoundException.of("Book", bookId));

        String resolvedType = (contentType == null || contentType.isBlank()) ? "image/jpeg" : contentType;
        String extension = extensionFor(resolvedType);
        String objectKey = "covers/" + bookId + "/cover." + extension;
        int expirySeconds = awsProperties.s3().uploadUrlExpirySeconds();
        Instant expiresAt = Instant.now().plusSeconds(expirySeconds);

        // Browser-reachable URL for LocalStack; s3:// for real AWS (clients use signed GET / CDN).
        String publicRef = publicCoverUrl(objectKey);
        book.setCoverUrl(publicRef);
        bookRepository.save(book);

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(awsProperties.s3().coverBucket())
                .key(objectKey)
                .contentType(resolvedType)
                .build();

        var presigned = s3Presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expirySeconds))
                .putObjectRequest(objectRequest)
                .build());

        String uploadUrl = browserReachableUrl(presigned.url().toString());
        log.info("Issued cover upload URL for bookId={} key={} expiresAt={}", bookId, objectKey, expiresAt);
        return new CoverUploadResponse(
                bookId,
                uploadUrl,
                objectKey,
                "PUT",
                resolvedType,
                expiresAt);
    }

    public Optional<CoverMetadataResponse> findMetadata(Long bookId) {
        if (!bookRepository.existsById(bookId)) {
            throw ResourceNotFoundException.of("Book", bookId);
        }

        var response = dynamoDb.getItem(GetItemRequest.builder()
                .tableName(awsProperties.dynamodb().coverMetadataTable())
                .key(Map.of("bookId", AttributeValue.builder().n(Long.toString(bookId)).build()))
                .consistentRead(true)
                .build());

        if (!response.hasItem() || response.item().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toResponse(response.item()));
    }

    private static CoverMetadataResponse toResponse(Map<String, AttributeValue> item) {
        return new CoverMetadataResponse(
                Long.parseLong(item.get("bookId").n()),
                stringAttr(item, "coverUrl"),
                stringAttr(item, "objectKey"),
                stringAttr(item, "contentType"),
                item.containsKey("sizeBytes") ? Long.parseLong(item.get("sizeBytes").n()) : null,
                item.containsKey("width") ? Integer.parseInt(item.get("width").n()) : null,
                item.containsKey("height") ? Integer.parseInt(item.get("height").n()) : null,
                stringAttr(item, "etag"),
                stringAttr(item, "status"),
                item.containsKey("processedAt") ? Instant.parse(item.get("processedAt").s()) : null);
    }

    private static String stringAttr(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        return value == null ? null : value.s();
    }

    private String publicCoverUrl(String objectKey) {
        String bucket = awsProperties.s3().coverBucket();
        if (awsProperties.hasCustomEndpoint()) {
            // Browser loads from the host; rewrite Docker DNS "localstack" → "localhost".
            String endpoint = awsProperties.endpoint().replaceAll("/$", "")
                    .replace("://localstack:", "://localhost:");
            return endpoint + "/" + bucket + "/" + objectKey;
        }
        return "s3://" + bucket + "/" + objectKey;
    }

    /** Host-side clients cannot resolve Docker DNS "localstack"; map to localhost. */
    private static String browserReachableUrl(String url) {
        return url
                .replace("://localstack:", "://localhost:")
                .replace(".localstack:", ".localhost:");
    }

    private static String extensionFor(String contentType) {
        return switch (contentType.toLowerCase()) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "jpg";
        };
    }
}
