package com.auctix.dto.auth;

import com.auctix.domain.User;

import java.time.Instant;

public record UserDto(
        Long id,
        String fullName,
        String email,
        String role,
        String status,
        String avatarUrl,
        String bio,
        Instant createdAt
) {
    public static UserDto from(User user) {
        return new UserDto(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().name(),
                user.getStatus().name(),
                user.getAvatarUrl(),
                user.getBio(),
                user.getCreatedAt());
    }
}
