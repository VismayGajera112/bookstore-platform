package com.example.book.dto;

import java.time.Instant;

public record BrowsingHistoryItem(
        Long bookId,
        String title,
        String authorName,
        String coverUrl,
        Instant viewedAt
) {
}
