package com.auctix.dto.message;

import jakarta.validation.constraints.NotNull;

/**
 * Opens a thread, or returns the existing one.
 *
 * @param auctionId optional lot the conversation is about; a separate thread is kept per lot so
 *                  questions about different items do not run together
 */
public record StartConversationRequest(
        @NotNull(message = "A recipient is required.") Long recipientId,
        Long auctionId) {
}
