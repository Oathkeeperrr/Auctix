package com.auctix.dto.auth;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(min = 2, max = 120) String fullName,
        @Size(max = 400) String bio,
        @Size(max = 300) String avatarUrl
) {
}
