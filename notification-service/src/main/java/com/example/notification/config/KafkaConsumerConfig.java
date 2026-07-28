package com.example.notification.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Retry + dead-letter wiring for {@code @KafkaListener} methods. A single {@link DefaultErrorHandler}
 * bean is picked up automatically by Spring Boot's auto-configured listener container factory — no
 * custom factory bean needed.
 *
 * <p>Two ways a record can fail here:
 * <ul>
 *   <li>the payload doesn't deserialize (poison pill) — reported as a {@link DeserializationException}
 *       by the {@code ErrorHandlingDeserializer} wrapper configured in application config; retrying
 *       this can never succeed, so it skips straight to the dead-letter topic</li>
 *   <li>the listener itself throws — retried with backoff a few times first, in case the failure is
 *       transient, before landing on the dead-letter topic</li>
 * </ul>
 */
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", -1));

        // 1s, 2s, 4s (~3 retries) before giving up on a genuinely failing (non-poison-pill) record.
        ExponentialBackOff backOff = new ExponentialBackOff(1_000L, 2.0);
        backOff.setMaxElapsedTime(7_000L);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(DeserializationException.class);
        return handler;
    }

    @Bean
    public NewTopic orderPlacedTopic(@Value("${app.kafka.topics.order-placed}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic orderPlacedDeadLetterTopic(@Value("${app.kafka.topics.order-placed}") String topic) {
        return TopicBuilder.name(topic + ".DLT").partitions(1).replicas(1).build();
    }
}
