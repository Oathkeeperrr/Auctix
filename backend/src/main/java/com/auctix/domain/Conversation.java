package com.auctix.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;

/**
 * A private thread between two people, optionally about one lot.
 *
 * <h2>Why the participants are ordered</h2>
 * A conversation between A and B is the same conversation as between B and A. Storing the
 * lower user id in {@code participantOne} makes that invariant structural rather than
 * conventional: one lookup finds an existing thread whichever way round the request arrives,
 * and the unique constraint can then actually prevent duplicates.
 */
@Entity
@Table(name = "conversations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_conversation_pair_auction",
                columnNames = {"participant_one_id", "participant_two_id", "auction_id"}),
        indexes = {
                @Index(name = "idx_conversations_one", columnList = "participant_one_id"),
                @Index(name = "idx_conversations_two", columnList = "participant_two_id"),
                @Index(name = "idx_conversations_recent", columnList = "last_message_at")
        })
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Always the participant with the lower id. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_one_id", nullable = false)
    private User participantOne;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_two_id", nullable = false)
    private User participantTwo;

    /** The lot this thread is about. Null for a general conversation. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auction_id")
    private Auction auction;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    /** Drives the ordering of the conversation list. Set to createdAt until the first message. */
    @Column(name = "last_message_at", nullable = false)
    private Instant lastMessageAt = Instant.now();

    protected Conversation() {
    }

    public Conversation(User participantOne, User participantTwo, Auction auction) {
        this.participantOne = participantOne;
        this.participantTwo = participantTwo;
        this.auction = auction;
        this.createdAt = Instant.now();
        this.lastMessageAt = this.createdAt;
    }

    /** True when this user is allowed to read the thread. */
    public boolean includes(Long userId) {
        return userId != null
                && (userId.equals(participantOne.getId()) || userId.equals(participantTwo.getId()));
    }

    /** The participant who is not the given user. */
    public User otherParty(Long userId) {
        return participantOne.getId().equals(userId) ? participantTwo : participantOne;
    }

    public Long getId() {
        return id;
    }

    public User getParticipantOne() {
        return participantOne;
    }

    public User getParticipantTwo() {
        return participantTwo;
    }

    public Auction getAuction() {
        return auction;
    }

    public void setAuction(Auction auction) {
        this.auction = auction;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastMessageAt() {
        return lastMessageAt;
    }

    public void setLastMessageAt(Instant lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Conversation other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
