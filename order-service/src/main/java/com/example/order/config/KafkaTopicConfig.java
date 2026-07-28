package com.example.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * A {@link org.apache.kafka.clients.admin.NewTopic} bean is picked up by the auto-configured
 * {@link KafkaAdmin} on startup and created if it does not already exist — idempotent, so it is safe
 * for order-service and its consumers to declare the same topic.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public org.apache.kafka.clients.admin.NewTopic orderPlacedTopic(
            @Value("${app.kafka.topics.order-placed}") String topic) {
        // Partitioned by order id (see OrderEventPublisher), so 3 partitions is enough to demonstrate
        // parallelism locally while keeping every event for one order on the same partition.
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }
}
