package com.example.user.dto;

public record LoginResponse(
        String token,
        String tokenType,
        long expiresInSeconds,
        UserResponse user
) {

    public static LoginResponse bearer(String token, long expiresInSeconds, UserResponse user) {
        return new LoginResponse(token, "Bearer", expiresInSeconds, user);
    }
}
