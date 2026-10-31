package com.auctix.dto.admin;

import jakarta.validation.constraints.Size;

public record ModerationRequest(
        @Size(max = 500) String reason
) {
}
