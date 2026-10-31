package com.auctix.dto.message;

import java.time.Instant;

/**
 * A row in the conversation list: who it is with, what was last said, and how much is unread.
 *
 * @param otherPartyLastSeen last sign-in of the other participant, which is as close to an
 *                           online indicator as the data honestly supports
 */
public record ConversationDto(
        Long id,
        Long otherPartyId,
        String otherPartyName,
        String otherPartyAvatarUrl,
        Instant otherPartyLastSeen,
        Long auctionId,
        String auctionTitle,
        String lastMessage,
        Long lastMessageSenderId,
        Instant lastMessageAt,
        long unreadCount) {
}
