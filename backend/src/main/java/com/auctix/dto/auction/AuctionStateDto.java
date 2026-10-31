package com.auctix.dto.auction;

import com.auctix.domain.Auction;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Compact live state of an auction. This is what every WebSocket event carries so a client
 * can re-render without a REST round trip, and what the countdown is driven from.
 */
public record AuctionStateDto(
        Long auctionId,
        String status,
        BigDecimal currentPrice,
        BigDecimal nextMinimumBid,
        BigDecimal minIncrement,
        int bidCount,
        long distinctBidders,
        Long highestBidderId,
        String highestBidderMasked,
        Instant startTime,
        Instant endTime,
        Instant serverTime,
        long secondsRemaining,
        int extensionCount,
        boolean reserveMet
) {
    public static AuctionStateDto from(Auction auction, long distinctBidders, Instant serverNow) {
        return new AuctionStateDto(
                auction.getId(),
                auction.getStatus().name(),
                auction.getCurrentPrice(),
                auction.nextMinimumBid(),
                auction.getMinIncrement(),
                auction.getBidCount(),
                distinctBidders,
                auction.getHighestBidder() == null ? null : auction.getHighestBidder().getId(),
                auction.getHighestBidder() == null ? null : auction.getHighestBidder().maskedName(),
                auction.getStartTime(),
                auction.getEndTime(),
                serverNow,
                auction.secondsRemaining(serverNow),
                auction.getExtensionCount(),
                auction.isReserveMet());
    }
}
