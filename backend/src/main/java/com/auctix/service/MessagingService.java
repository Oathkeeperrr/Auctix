package com.auctix.service;

import com.auctix.domain.Auction;
import com.auctix.domain.Conversation;
import com.auctix.domain.Message;
import com.auctix.domain.Role;
import com.auctix.domain.User;
import com.auctix.dto.message.ConversationDto;
import com.auctix.dto.message.MessageDto;
import com.auctix.dto.message.MessagingEvent;
import com.auctix.exception.ApiException;
import com.auctix.exception.ErrorCode;
import com.auctix.repository.AuctionRepository;
import com.auctix.repository.ConversationRepository;
import com.auctix.repository.MessageRepository;
import com.auctix.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Private messaging between buyers and sellers.
 *
 * <h2>Where permission is decided</h2>
 * Every method that touches a thread goes through {@link #requireAccess}, which loads the
 * conversation and refuses anyone who is not a participant. Authorisation is never inferred
 * from the request: the client sends only a conversation id, and the server decides from the
 * stored participants whether that id is any of the caller's business. An administrator may
 * read a thread for moderation but is deliberately not allowed to post into it.
 *
 * <h2>Delivery</h2>
 * New messages and read receipts are pushed to the other participant's private STOMP queue,
 * the same mechanism notifications already use, so only that user receives them. Delivery is
 * direct through {@link SimpMessagingTemplate} rather than the Redis event bus, which means it
 * is correct for a single backend instance; running several would need the bus, as auction
 * events already do.
 */
@Service
public class MessagingService {

    private static final Logger log = LoggerFactory.getLogger(MessagingService.class);

    /** Where a participant listens. The client subscribes to /user/queue/messages. */
    public static final String USER_QUEUE = "/queue/messages";

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final AuctionRepository auctionRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public MessagingService(ConversationRepository conversationRepository,
                            MessageRepository messageRepository,
                            UserRepository userRepository,
                            AuctionRepository auctionRepository,
                            SimpMessagingTemplate messagingTemplate) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.auctionRepository = auctionRepository;
        this.messagingTemplate = messagingTemplate;
    }

    // ---------------------------------------------------------------- reading

    @Transactional(readOnly = true)
    public List<ConversationDto> conversations(Long userId) {
        List<Conversation> threads = conversationRepository.findForUser(userId);
        List<ConversationDto> out = new ArrayList<>(threads.size());
        for (Conversation thread : threads) {
            out.add(describe(thread, userId));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<MessageDto> thread(Long conversationId, Long userId, Role role) {
        requireAccess(conversationId, userId, role);
        return messageRepository.findThread(conversationId).stream()
                .map(message -> MessageDto.from(message, userId))
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return messageRepository.countUnreadForUser(userId);
    }

    // ---------------------------------------------------------------- writing

    /**
     * Finds the thread between these two people about this lot, or starts one.
     *
     * <p>Participants are stored id-ordered, so the same pair always resolves to the same row
     * whichever of them opens it first.</p>
     */
    @Transactional
    public ConversationDto startOrFind(Long userId, Long recipientId, Long auctionId) {
        if (userId.equals(recipientId)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "You cannot message yourself.");
        }
        User me = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.USER_NOT_FOUND, userId));
        User them = userRepository.findById(recipientId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.USER_NOT_FOUND, recipientId));

        User one = me.getId() < them.getId() ? me : them;
        User two = me.getId() < them.getId() ? them : me;

        Conversation existing = (auctionId == null
                ? conversationRepository.findByParticipantOneIdAndParticipantTwoIdAndAuctionIsNull(
                        one.getId(), two.getId())
                : conversationRepository.findByParticipantOneIdAndParticipantTwoIdAndAuctionId(
                        one.getId(), two.getId(), auctionId))
                .orElse(null);

        if (existing != null) {
            return describe(existing, userId);
        }

        Auction auction = null;
        if (auctionId != null) {
            auction = auctionRepository.findById(auctionId)
                    .orElseThrow(() -> ApiException.notFound(ErrorCode.AUCTION_NOT_FOUND, auctionId));
        }
        Conversation saved = conversationRepository.save(new Conversation(one, two, auction));
        log.debug("Opened conversation {} between {} and {}", saved.getId(), one.getId(), two.getId());
        return describe(saved, userId);
    }

    @Transactional
    public MessageDto send(Long conversationId, Long senderId, String body) {
        Conversation thread = requireAccess(conversationId, senderId, null);
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.USER_NOT_FOUND, senderId));

        Message saved = messageRepository.save(new Message(thread, sender, body.trim()));
        thread.setLastMessageAt(saved.getSentAt());
        conversationRepository.save(thread);

        User recipient = thread.otherParty(senderId);
        // Each side is told about the message from its own point of view, so "mine" is correct
        // for whoever receives it.
        messagingTemplate.convertAndSendToUser(recipient.getEmail(), USER_QUEUE,
                MessagingEvent.message(MessageDto.from(saved, recipient.getId()), senderId));

        return MessageDto.from(saved, senderId);
    }

    /** Marks everything the reader did not send as read, and tells the sender. */
    @Transactional
    public long markRead(Long conversationId, Long readerId) {
        Conversation thread = requireAccess(conversationId, readerId, null);
        int updated = messageRepository.markThreadRead(conversationId, readerId, Instant.now());
        if (updated > 0) {
            User other = thread.otherParty(readerId);
            messagingTemplate.convertAndSendToUser(other.getEmail(), USER_QUEUE,
                    MessagingEvent.read(conversationId, readerId));
        }
        return updated;
    }

    // ---------------------------------------------------------------- internals

    /**
     * Loads a thread and refuses anyone who has no business reading it.
     *
     * @param role when ADMIN, read access is granted for moderation; pass null to require
     *             participation, which is what writing needs
     */
    private Conversation requireAccess(Long conversationId, Long userId, Role role) {
        Conversation thread = conversationRepository.findById(conversationId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.CONVERSATION_NOT_FOUND, conversationId));
        if (thread.includes(userId) || role == Role.ADMIN) {
            return thread;
        }
        // Reported as not-found rather than forbidden: confirming a thread exists would leak
        // that two particular people are talking.
        throw ApiException.notFound(ErrorCode.CONVERSATION_NOT_FOUND, conversationId);
    }

    private ConversationDto describe(Conversation thread, Long viewerId) {
        User other = thread.otherParty(viewerId);
        Message last = messageRepository
                .findFirstByConversationIdOrderBySentAtDesc(thread.getId())
                .orElse(null);
        long unread = messageRepository
                .countByConversationIdAndSenderIdNotAndReadAtIsNull(thread.getId(), viewerId);

        return new ConversationDto(
                thread.getId(),
                other.getId(),
                other.getFullName(),
                other.getAvatarUrl(),
                other.getLastLoginAt(),
                thread.getAuction() == null ? null : thread.getAuction().getId(),
                thread.getAuction() == null ? null : thread.getAuction().getItem().getTitle(),
                last == null ? null : last.getBody(),
                last == null ? null : last.getSender().getId(),
                thread.getLastMessageAt(),
                unread);
    }
}
