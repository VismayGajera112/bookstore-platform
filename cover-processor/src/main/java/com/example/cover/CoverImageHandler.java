package com.example.cover;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * S3 ObjectCreated → extract cover metadata → DynamoDB ({@code CoverMetadata}) → SNS email.
 *
 * <p>Idempotency: the DynamoDB item is keyed by {@code bookId}. A conditional put succeeds only when
 * the item is new or the S3 {@code etag} differs from the stored one. SNS is published only after a
 * successful put, so a re-delivered event for the same object never sends a duplicate email.
 *
 * <p>Object key convention: {@code covers/{bookId}/...}
 */
public class CoverImageHandler implements RequestHandler<S3Event, String> {

    private static final Logger log = LoggerFactory.getLogger(CoverImageHandler.class);
    private static final Pattern BOOK_ID_FROM_KEY = Pattern.compile("^covers/(\\d+)/");

    private final S3Client s3;
    private final DynamoDbClient dynamoDb;
    private final SnsClient sns;
    private final String tableName;
    private final String topicArn;

    public CoverImageHandler() {
        this(buildS3(), buildDynamoDb(), buildSns(),
                env("COVER_METADATA_TABLE", "CoverMetadata"),
                env("COVER_PROCESSED_TOPIC_ARN", ""));
    }

    CoverImageHandler(S3Client s3, DynamoDbClient dynamoDb, SnsClient sns,
                      String tableName, String topicArn) {
        this.s3 = s3;
        this.dynamoDb = dynamoDb;
        this.sns = sns;
        this.tableName = tableName;
        this.topicArn = topicArn;
    }

    @Override
    public String handleRequest(S3Event event, Context context) {
        if (event.getRecords() == null || event.getRecords().isEmpty()) {
            return "no-records";
        }

        int processed = 0;
        for (S3EventNotification.S3EventNotificationRecord record : event.getRecords()) {
            String bucket = record.getS3().getBucket().getName();
            String key = record.getS3().getObject().getUrlDecodedKey();
            processOne(bucket, key);
            processed++;
        }
        return "processed=" + processed;
    }

    private void processOne(String bucket, String key) {
        Long bookId = parseBookId(key);
        if (bookId == null) {
            log.warn("Skipping object with unexpected key (expected covers/{{bookId}}/...): {}", key);
            return;
        }

        HeadObjectResponse head = s3.headObject(HeadObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());

        String etag = stripQuotes(head.eTag());
        String contentType = head.contentType() != null ? head.contentType() : "application/octet-stream";
        long sizeBytes = head.contentLength() != null ? head.contentLength() : 0L;

        Integer width = null;
        Integer height = null;
        if (contentType.startsWith("image/")) {
            ResponseBytes<GetObjectResponse> bytes = s3.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            try {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes.asByteArray()));
                if (image != null) {
                    width = image.getWidth();
                    height = image.getHeight();
                }
            } catch (Exception ex) {
                log.warn("Could not read image dimensions for s3://{}/{}: {}", bucket, key, ex.getMessage());
            }
        }

        String coverUrl = "s3://" + bucket + "/" + key;
        Instant now = Instant.now();

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("bookId", AttributeValue.builder().n(Long.toString(bookId)).build());
        item.put("objectKey", AttributeValue.builder().s(key).build());
        item.put("bucket", AttributeValue.builder().s(bucket).build());
        item.put("coverUrl", AttributeValue.builder().s(coverUrl).build());
        item.put("contentType", AttributeValue.builder().s(contentType).build());
        item.put("sizeBytes", AttributeValue.builder().n(Long.toString(sizeBytes)).build());
        item.put("etag", AttributeValue.builder().s(etag).build());
        item.put("processedAt", AttributeValue.builder().s(now.toString()).build());
        item.put("status", AttributeValue.builder().s("PROCESSED").build());
        if (width != null) {
            item.put("width", AttributeValue.builder().n(Integer.toString(width)).build());
        }
        if (height != null) {
            item.put("height", AttributeValue.builder().n(Integer.toString(height)).build());
        }

        // Succeeds only for a new bookId or a genuinely new object (different etag).
        try {
            dynamoDb.putItem(PutItemRequest.builder()
                    .tableName(tableName)
                    .item(item)
                    .conditionExpression("attribute_not_exists(bookId) OR etag <> :etag")
                    .expressionAttributeValues(Map.of(
                            ":etag", AttributeValue.builder().s(etag).build()))
                    .build());
        } catch (ConditionalCheckFailedException ex) {
            log.info("Idempotent skip for bookId={} etag={} (already processed)", bookId, etag);
            return;
        }

        publishProcessed(bookId, coverUrl, contentType, sizeBytes, width, height);
        log.info("Cover metadata written for bookId={} key={}", bookId, key);
    }

    private void publishProcessed(Long bookId, String coverUrl, String contentType,
                                  long sizeBytes, Integer width, Integer height) {
        if (topicArn == null || topicArn.isBlank()) {
            log.warn("COVER_PROCESSED_TOPIC_ARN unset; skipping SNS notification for bookId={}", bookId);
            return;
        }

        String body = """
                Cover for book %d processed successfully.

                URL: %s
                Content-Type: %s
                Size: %d bytes
                Dimensions: %sx%s
                """.formatted(
                bookId,
                coverUrl,
                contentType,
                sizeBytes,
                width != null ? width : "?",
                height != null ? height : "?");

        sns.publish(PublishRequest.builder()
                .topicArn(topicArn)
                .subject("Cover processed for book " + bookId)
                .message(body)
                .build());
    }

    static Long parseBookId(String key) {
        Matcher matcher = BOOK_ID_FROM_KEY.matcher(key);
        if (!matcher.find()) {
            return null;
        }
        return Long.parseLong(matcher.group(1));
    }

    private static String stripQuotes(String etag) {
        if (etag == null) {
            return "";
        }
        return etag.replace("\"", "");
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static URI optionalEndpoint() {
        String endpoint = System.getenv("AWS_ENDPOINT_URL");
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = System.getenv("LOCALSTACK_ENDPOINT");
        }
        return endpoint == null || endpoint.isBlank() ? null : URI.create(endpoint);
    }

    private static Region region() {
        return Region.of(env("AWS_REGION", "us-east-1"));
    }

    private static ClientOverrideConfiguration clientOverrides() {
        return ClientOverrideConfiguration.builder()
                .retryPolicy(RetryPolicy.builder().numRetries(2).build())
                .build();
    }

    private static S3Client buildS3() {
        var builder = S3Client.builder().region(region()).overrideConfiguration(clientOverrides());
        URI endpoint = optionalEndpoint();
        if (endpoint != null) {
            builder.endpointOverride(endpoint).forcePathStyle(true);
        }
        return builder.build();
    }

    private static DynamoDbClient buildDynamoDb() {
        var builder = DynamoDbClient.builder().region(region()).overrideConfiguration(clientOverrides());
        URI endpoint = optionalEndpoint();
        if (endpoint != null) {
            builder.endpointOverride(endpoint);
        }
        return builder.build();
    }

    private static SnsClient buildSns() {
        var builder = SnsClient.builder().region(region()).overrideConfiguration(clientOverrides());
        URI endpoint = optionalEndpoint();
        if (endpoint != null) {
            builder.endpointOverride(endpoint);
        }
        return builder.build();
    }
}
