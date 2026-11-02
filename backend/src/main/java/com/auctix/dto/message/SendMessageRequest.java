package com.auctix.dto.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotBlank(message = "A message cannot be empty.")
        @Size(max = 2000, message = "A message may be at most 2000 characters.")
        String body) {
}
