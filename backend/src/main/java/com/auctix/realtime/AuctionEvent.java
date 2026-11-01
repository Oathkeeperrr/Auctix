package com.auctix.realtime;

import com.auctix.dto.auction.AuctionStateDto;
import com.auctix.dto.bid.BidDto;
import com.auctix.dto.notification.NotificationDto;

import java.time.Instant;

/**
 * Everything that travels over Redis pub/sub and out through STOMP.
 *
 * <p>When {@link #targetUser()} is {@code null} the event is broadcast to
 * {@code /topics/auction/{auctionId}} and the global {@code /topics/auctions} feed.
 * When it is set the event is delivered privately to {@code /user/queue/notifications}.</p>
 */
public record AuctionEvent(
        AuctionEventType type,
        Long auctionId,
        Instant serverTime,
        AuctionStateDto state,
        BidDto bid,
        NotificationDto notification,
        String message,
        String targetUser
) {

    public static AuctionEvent broadcast(AuctionEventType type, Long auctionId,
                                         AuctionStateDto state, BidDto bid, String message) {
        return new AuctionEvent(type, auctionId, Instant.now(), state, bid, null, message, null);
    }

    public static AuctionEvent toUser(String userEmail, AuctionEventType type, Long auctionId,
                                      NotificationDto notification, AuctionStateDto state) {
        return new AuctionEvent(type, auctionId, Instant.now(), state, null, notification,
                notification == null ? null : notification.message(), userEmail);
    }

    public boolean isPrivate() {
        return targetUser != null && !targetUser.isBlank();
    }
}
