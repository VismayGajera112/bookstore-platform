package com.example.book.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bookstore.aws")
public record AwsProperties(
        String region,
        String endpoint,
        S3Properties s3,
        DynamoProperties dynamodb
) {
    public record S3Properties(
            String coverBucket,
            int uploadUrlExpirySeconds
    ) {
    }

    public record DynamoProperties(
            String coverMetadataTable,
            String browsingHistoryTable,
            int browsingHistoryTtlDays
    ) {
    }

    public boolean hasCustomEndpoint() {
        return endpoint != null && !endpoint.isBlank();
    }
}
