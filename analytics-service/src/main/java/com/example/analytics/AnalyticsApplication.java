package com.example.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * A pure event consumer, same shape as notification-service but a separate consumer group: both
 * services receive every {@code order-placed} record independently (broadcast, not competing consumers
 * across services). No controllers, no public REST surface beyond {@code /actuator/health}.
 */
@SpringBootApplication
@EnableScheduling
public class AnalyticsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalyticsApplication.class, args);
    }
}
