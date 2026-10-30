package com.auctix.domain;

import java.util.Set;

/**
 * Auction lifecycle state machine.
 *
 * <pre>
 * PENDING_APPROVAL -> SCHEDULED -> LIVE -> ENDING -> CLOSED -> SETTLED -> ARCHIVED
 *                  -> REJECTED
 *                               -> CANCELLED
 * </pre>
 */
public enum AuctionStatus {
    PENDING_APPROVAL,
    REJECTED,
    SCHEDULED,
    LIVE,
    ENDING,
    CLOSED,
    SETTLED,
    ARCHIVED,
    CANCELLED;

    /** True when bids may be accepted in this state. */
    public boolean acceptsBids() {
        return this == LIVE || this == ENDING;
    }

    public boolean isTerminal() {
        return this == CLOSED || this == SETTLED || this == ARCHIVED
                || this == CANCELLED || this == REJECTED;
    }

    public Set<AuctionStatus> allowedTransitions() {
        return switch (this) {
            case PENDING_APPROVAL -> Set.of(SCHEDULED, REJECTED, CANCELLED);
            case REJECTED -> Set.of(PENDING_APPROVAL, CANCELLED);
            case SCHEDULED -> Set.of(LIVE, CANCELLED);
            case LIVE -> Set.of(ENDING, CLOSED, CANCELLED);
            case ENDING -> Set.of(LIVE, CLOSED, CANCELLED);
            case CLOSED -> Set.of(SETTLED);
            case SETTLED -> Set.of(ARCHIVED);
            case ARCHIVED, CANCELLED -> Set.of();
        };
    }

    public boolean canTransitionTo(AuctionStatus target) {
        return allowedTransitions().contains(target);
    }
}
