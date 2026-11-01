package com.auctix.dto.message;

import com.auctix.domain.Message;

import java.time.Instant;

/**
 * One message as the client sees it.
 *
 * @param mine   whether the viewer sent it, so the UI need not compare ids
 * @param readAt null until the recipient opens the thread
 */
public record MessageDto(
        Long id,
        Long conversationId,
        Long senderId,
        String senderName,
        String body,
        Instant sentAt,
        Instant readAt,
        boolean mine) {

    public static MessageDto from(Message message, Long viewerId) {
        return new MessageDto(
                message.getId(),
                message.getConversation().getId(),
                message.getSender().getId(),
                message.getSender().getFullName(),
                message.getBody(),
                message.getSentAt(),
                message.getReadAt(),
                message.getSender().getId().equals(viewerId));
    }
}
