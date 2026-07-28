package com.example.notification.monitor;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Nothing consumes {@code order-placed.DLT} — that is the point of a dead-letter topic, a human decides
 * what to do with a record that has already failed repeatedly. This component is the eyes on it: every
 * {@code interval-ms} it asks the broker for the earliest and latest offset of every partition of the
 * DLT and publishes "latest − earliest" as a Micrometer gauge, so depth is visible at
 * {@code /actuator/metrics/kafka.dlt.depth} (and can be scraped/alerted on like any other metric).
 */
@Component
public class DlqDepthMonitor {

    private static final Logger log = LoggerFactory.getLogger(DlqDepthMonitor.class);

    private final KafkaAdmin kafkaAdmin;
    private final String dlqTopic;
    private final AtomicLong depth = new AtomicLong();

    public DlqDepthMonitor(KafkaAdmin kafkaAdmin,
                           @Value("${app.kafka.topics.order-placed}") String orderPlacedTopic,
                           MeterRegistry meterRegistry) {
        this.kafkaAdmin = kafkaAdmin;
        this.dlqTopic = orderPlacedTopic + ".DLT";
        Gauge.builder("kafka.dlt.depth", depth, AtomicLong::get)
                .tag("topic", dlqTopic)
                .description("Records currently sitting in the dead-letter topic, awaiting manual review")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${app.kafka.dlq-monitor.interval-ms:30000}")
    public void refresh() {
        try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            TopicDescription description = admin.describeTopics(List.of(dlqTopic))
                    .topicNameValues().get(dlqTopic).get();
            List<TopicPartition> partitions = description.partitions().stream()
                    .map(p -> new TopicPartition(dlqTopic, p.partition()))
                    .toList();

            Map<TopicPartition, OffsetSpec> earliestRequest = partitions.stream()
                    .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.earliest()));
            Map<TopicPartition, OffsetSpec> latestRequest = partitions.stream()
                    .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest()));

            var earliest = admin.listOffsets(earliestRequest).all().get();
            var latest = admin.listOffsets(latestRequest).all().get();

            long total = partitions.stream()
                    .mapToLong(tp -> latest.get(tp).offset() - earliest.get(tp).offset())
                    .sum();

            depth.set(total);
            if (total > 0) {
                log.warn("Dead-letter topic {} currently holds {} message(s) awaiting manual review",
                        dlqTopic, total);
            }
        } catch (Exception ex) {
            // Most likely the topic doesn't exist yet because nothing has ever failed permanently —
            // a healthy state, not one worth logging loudly.
            log.debug("Could not read depth of {}: {}", dlqTopic, ex.getMessage());
        }
    }
}
