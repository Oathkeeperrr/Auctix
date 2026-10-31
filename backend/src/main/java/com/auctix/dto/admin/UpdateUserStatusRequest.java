package com.auctix.dto.admin;

import com.auctix.domain.UserStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateUserStatusRequest(
        @NotNull UserStatus status,
        @Size(max = 500) String reason
) {
}
