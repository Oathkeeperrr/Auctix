package com.auctix.dto.assistant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A question for the assistant.
 *
 * @param auctionId the lot the person is looking at, when they are on a lot's page, so
 *                  "what is the current bid" can be answered without them naming it
 */
public record AssistantQuery(
        @NotBlank(message = "Ask a question.")
        @Size(max = 500, message = "Questions may be at most 500 characters.")
        String question,
        Long auctionId) {
}
