package com.example.common.config;

import com.example.common.aop.LoggingAspect;
import com.example.common.security.JwtAuthenticationFilter;
import com.example.common.security.JwtProperties;
import com.example.common.security.JwtUtil;
import com.example.common.web.ConfigDemoController;
import com.example.common.web.GlobalExceptionHandler;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the cross-cutting pieces every service needs. Each service imports this and then defines its
 * own {@code SecurityConfig}, because the route rules differ per service even though token
 * verification does not.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class CommonConfig {

    @Bean
    public JwtUtil jwtUtil(JwtProperties properties) {
        return new JwtUtil(properties);
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtUtil jwtUtil) {
        return new JwtAuthenticationFilter(jwtUtil);
    }

    @Bean
    public LoggingAspect loggingAspect() {
        return new LoggingAspect();
    }

    /**
     * Refresh-scoped so editing {@code config-repo/application.yml} and calling
     * {@code POST /actuator/refresh} updates the demo message without a restart.
     */
    @Bean
    @RefreshScope
    @ConfigurationProperties(prefix = "bookstore.demo")
    public DemoProperties demoProperties() {
        return new DemoProperties();
    }

    @Bean
    public ConfigDemoController configDemoController(DemoProperties demoProperties,
                                                     @org.springframework.beans.factory.annotation.Value("${spring.application.name:unknown}")
                                                     String serviceName) {
        return new ConfigDemoController(demoProperties, serviceName);
    }

    @Bean
    public GlobalExceptionHandler globalExceptionHandler(
            @org.springframework.beans.factory.annotation.Value("${spring.application.name:unknown}")
            String serviceName) {
        return new GlobalExceptionHandler(serviceName);
    }
}
