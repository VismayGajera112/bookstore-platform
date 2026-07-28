package com.example.book.dto;

import java.time.Instant;

/** Presigned upload target returned to an ADMIN before they PUT the cover bytes to S3. */
public record CoverUploadResponse(
        Long bookId,
        String uploadUrl,
        String objectKey,
        String method,
        String contentType,
        Instant expiresAt
) {
}
