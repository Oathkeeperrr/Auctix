package com.auctix.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;

/**
 * One message in a conversation.
 *
 * <p>{@code readAt} is the read receipt: null means the recipient has not opened the thread
 * since it arrived. It is only ever set on messages the reader did not send, so a sender can
 * never mark their own message as read.</p>
 */
@Entity
@Table(name = "messages",
        indexes = {
                @Index(name = "idx_messages_conversation", columnList = "conversation_id, sent_at"),
                @Index(name = "idx_messages_unread", columnList = "conversation_id, read_at")
        })
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(nullable = false, length = 2000)
    private String body;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt = Instant.now();

    @Column(name = "read_at")
    private Instant readAt;

    protected Message() {
    }

    public Message(Conversation conversation, User sender, String body) {
        this.conversation = conversation;
        this.sender = sender;
        this.body = body;
        this.sentAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public User getSender() {
        return sender;
    }

    public String getBody() {
        return body;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public void setReadAt(Instant readAt) {
        this.readAt = readAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Message other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
