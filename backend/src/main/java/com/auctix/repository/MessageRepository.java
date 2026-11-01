package com.auctix.repository;

import com.auctix.domain.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("select m from Message m join fetch m.sender "
            + "where m.conversation.id = :conversationId order by m.sentAt asc")
    List<Message> findThread(@Param("conversationId") Long conversationId);

    Optional<Message> findFirstByConversationIdOrderBySentAtDesc(Long conversationId);

    /** Unread in one thread, for the conversation list badge. */
    long countByConversationIdAndSenderIdNotAndReadAtIsNull(Long conversationId, Long senderId);

    /** Unread across every thread, for the navbar badge. */
    @Query("select count(m) from Message m where m.sender.id <> :userId and m.readAt is null "
            + "and (m.conversation.participantOne.id = :userId "
            + "or m.conversation.participantTwo.id = :userId)")
    long countUnreadForUser(@Param("userId") Long userId);

    /**
     * Marks everything the reader did not send as read.
     *
     * <p>The {@code sender.id <> :readerId} clause is what stops a sender marking their own
     * messages read, which would make the receipt meaningless.</p>
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Message m set m.readAt = :now where m.conversation.id = :conversationId "
            + "and m.sender.id <> :readerId and m.readAt is null")
    int markThreadRead(@Param("conversationId") Long conversationId,
                       @Param("readerId") Long readerId,
                       @Param("now") Instant now);
}
