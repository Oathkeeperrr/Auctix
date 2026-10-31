package com.auctix.dto.auth;

public record AuthResponse(
        String token,
        String tokenType,
        long expiresInMinutes,
        UserDto user
) {
    public static AuthResponse bearer(String token, long expiresInMinutes, UserDto user) {
        return new AuthResponse(token, "Bearer", expiresInMinutes, user);
    }
}
