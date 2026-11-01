package com.auctix.dto.message;

import java.time.Instant;

/**
 * What is pushed to a participant's private STOMP queue.
 *
 * @param type    {@code MESSAGE} for a new message, {@code READ} when the other party has read
 *                the thread
 * @param message present only on {@code MESSAGE}
 * @param byUserId who caused the event
 */
public record MessagingEvent(
        String type,
        Long conversationId,
        MessageDto message,
        Long byUserId,
        Instant at) {

    public static MessagingEvent message(MessageDto message, Long senderId) {
        return new MessagingEvent("MESSAGE", message.conversationId(), message, senderId, Instant.now());
    }

    public static MessagingEvent read(Long conversationId, Long readerId) {
        return new MessagingEvent("READ", conversationId, null, readerId, Instant.now());
    }
}
