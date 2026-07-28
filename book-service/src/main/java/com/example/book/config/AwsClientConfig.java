package com.example.book.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * AWS clients for cover uploads (S3) and DynamoDB tables. When {@code bookstore.aws.endpoint} is set
 * (LocalStack), path-style S3 and static test credentials are used.
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(AwsProperties.class)
@ConditionalOnProperty(prefix = "bookstore.aws", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AwsClientConfig {

    @Bean
    public S3Client s3Client(AwsProperties props) {
        var builder = S3Client.builder().region(Region.of(props.region()));
        if (props.hasCustomEndpoint()) {
            builder.endpointOverride(URI.create(props.endpoint()))
                    .forcePathStyle(true)
                    .credentialsProvider(localCredentials());
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner(AwsProperties props) {
        var builder = S3Presigner.builder().region(Region.of(props.region()));
        if (props.hasCustomEndpoint()) {
            builder.endpointOverride(URI.create(props.endpoint()))
                    .credentialsProvider(localCredentials());
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        return builder.build();
    }

    @Bean
    public DynamoDbClient dynamoDbClient(AwsProperties props) {
        var builder = DynamoDbClient.builder().region(Region.of(props.region()));
        if (props.hasCustomEndpoint()) {
            builder.endpointOverride(URI.create(props.endpoint()))
                    .credentialsProvider(localCredentials());
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        return builder.build();
    }

    private static StaticCredentialsProvider localCredentials() {
        // LocalStack accepts any credentials; these match the compose init defaults.
        return StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"));
    }
}
