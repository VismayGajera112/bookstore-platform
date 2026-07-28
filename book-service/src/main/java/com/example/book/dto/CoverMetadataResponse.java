package com.example.book.dto;

import java.time.Instant;

/** Cover URL plus Lambda-processed metadata from the CoverMetadata DynamoDB table. */
public record CoverMetadataResponse(
        Long bookId,
        String coverUrl,
        String objectKey,
        String contentType,
        Long sizeBytes,
        Integer width,
        Integer height,
        String etag,
        String status,
        Instant processedAt
) {
}
