package com.example.user.dto;

import com.example.user.entity.User;

import java.time.Instant;

/** Deliberately omits {@code passwordHash} so a hash can never leak through the API. */
public record UserResponse(
        Long id,
        String username,
        String email,
        String role,
        Instant createdAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                user.getCreatedAt());
    }
}
