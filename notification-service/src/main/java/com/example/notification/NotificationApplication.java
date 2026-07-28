package com.example.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * A pure event consumer: no controllers, no public REST surface. The only externally visible endpoint
 * is {@code /actuator/health}, wired for container liveness/readiness probes. Everything it does
 * happens by reacting to {@code order-placed} records on its own consumer group.
 */
@SpringBootApplication
@EnableScheduling
public class NotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}
