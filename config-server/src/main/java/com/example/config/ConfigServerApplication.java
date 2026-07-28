package com.example.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Serves per-service YAML from {@code config-repo/}. Microservices call this on startup via
 * {@code spring.config.import=configserver:...} instead of shipping their own copies of shared
 * settings.
 *
 * <p>On Kubernetes this role is usually filled by ConfigMaps and Secrets (Step 10). The Config
 * Server is the classic Spring Cloud equivalent — same idea, different delivery mechanism.
 */
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
