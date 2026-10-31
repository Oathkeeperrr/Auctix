package com.auctix.dto.admin;

import com.auctix.domain.User;

import java.time.Instant;

public record AdminUserDto(
        Long id,
        String fullName,
        String email,
        String role,
        String status,
        Instant createdAt,
        Instant lastLoginAt,
        long auctionsCreated,
        long bidsPlaced
) {
    public static AdminUserDto from(User user, long auctionsCreated, long bidsPlaced) {
        return new AdminUserDto(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().name(),
                user.getStatus().name(),
                user.getCreatedAt(),
                user.getLastLoginAt(),
                auctionsCreated,
                bidsPlaced);
    }
}
