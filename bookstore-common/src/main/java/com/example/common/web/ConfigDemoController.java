package com.example.common.web;

import com.example.common.config.DemoProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Tiny probe used in Step 6 to prove a central config change can be picked up via refresh.
 * Not part of the bookstore domain API.
 */
@RestController
@RequestMapping("/api/config")
public class ConfigDemoController {

    private final DemoProperties demoProperties;
    private final String serviceName;

    public ConfigDemoController(DemoProperties demoProperties,
                                @org.springframework.beans.factory.annotation.Value("${spring.application.name}")
                                String serviceName) {
        this.demoProperties = demoProperties;
        this.serviceName = serviceName;
    }

    @GetMapping("/demo")
    public Map<String, String> demo() {
        return Map.of(
                "service", serviceName,
                "message", demoProperties.getMessage()
        );
    }
}
