package com.auctix.dto.auth;

import com.auctix.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 120)
        String fullName,

        @NotBlank(message = "E-mail is required")
        @Email(message = "Enter a valid e-mail address")
        @Size(max = 180)
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        @Pattern(regexp = ".*[A-Za-z].*", message = "Password must contain a letter")
        @Pattern(regexp = ".*\\d.*", message = "Password must contain a digit")
        String password,

        Role role
) {
    public Role roleOrDefault() {
        return role == null ? Role.BIDDER : role;
    }
}
