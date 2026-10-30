package com.auctix.dto.admin;

import com.auctix.domain.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(@NotNull Role role) {
}
