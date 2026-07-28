package com.example.book.security;

import com.example.common.security.JsonAuthenticationErrorHandlers;
import com.example.common.security.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * book-service verifies tokens with the shared secret and never calls user-service to do it. That is
 * what lets the catalog keep serving reads and reservations while user-service is down.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;
    private final String serviceName;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          ObjectMapper objectMapper,
                          @Value("${spring.application.name}") String serviceName) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
        this.serviceName = serviceName;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Catalog reads are public; /availability is excluded because it is a
                        // service-to-service call that must carry the customer's identity.
                        .requestMatchers(HttpMethod.GET, "/api/books/availability").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/books/**", "/api/authors/**").permitAll()
                        .requestMatchers("/api/config/demo").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/books/reservations/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/books/**", "/api/authors/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/books/**", "/api/authors/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/books/**", "/api/authors/**").hasRole("ADMIN")
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(
                                JsonAuthenticationErrorHandlers.unauthorized(objectMapper, serviceName))
                        .accessDeniedHandler(
                                JsonAuthenticationErrorHandlers.forbidden(objectMapper, serviceName)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
