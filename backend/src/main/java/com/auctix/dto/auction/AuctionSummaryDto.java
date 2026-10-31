package com.auctix.dto.auction;

import com.auctix.domain.Auction;

import java.math.BigDecimal;
import java.time.Instant;

public record AuctionSummaryDto(
        Long id,
        String title,
        String category,
        String categoryLabel,
        String modelKind,
        String imageUrl,
        String status,
        BigDecimal startingPrice,
        BigDecimal currentPrice,
        BigDecimal nextMinimumBid,
        int bidCount,
        Instant startTime,
        Instant endTime,
        Instant serverTime,
        long secondsRemaining,
        String sellerName,
        boolean watched,
        long watchCount
) {
    public static AuctionSummaryDto from(Auction auction, Instant serverNow, boolean watched, long watchCount) {
        return new AuctionSummaryDto(
                auction.getId(),
                auction.getItem().getTitle(),
                auction.getItem().getCategory().name(),
                auction.getItem().getCategory().getLabel(),
                auction.getItem().getModelKind().name(),
                auction.getItem().getImageUrl(),
                auction.getStatus().name(),
                auction.getStartingPrice(),
                auction.getCurrentPrice(),
                auction.nextMinimumBid(),
                auction.getBidCount(),
                auction.getStartTime(),
                auction.getEndTime(),
                serverNow,
                auction.secondsRemaining(serverNow),
                auction.getSeller().getFullName(),
                watched,
                watchCount);
    }
}
