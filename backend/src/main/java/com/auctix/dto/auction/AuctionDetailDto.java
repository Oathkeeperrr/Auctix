package com.auctix.dto.auction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.auctix.dto.bid.BidDto;

public record AuctionDetailDto(
        Long id,
        String title,
        String description,
        String category,
        String categoryLabel,
        String modelKind,
        String imageUrl,
        String brand,
        String conditionLabel,
        String status,
        BigDecimal startingPrice,
        BigDecimal currentPrice,
        BigDecimal nextMinimumBid,
        BigDecimal minIncrement,
        /** Visible to the seller and administrators only; null for everyone else. */
        BigDecimal reservePrice,
        boolean reserveMet,
        int bidCount,
        long distinctBidders,
        Long highestBidderId,
        String highestBidderMasked,
        Instant startTime,
        Instant endTime,
        Instant originalEndTime,
        Instant serverTime,
        long secondsRemaining,
        int extensionCount,
        Long sellerId,
        String sellerName,
        Instant sellerJoinedAt,
        String winnerMasked,
        BigDecimal winningAmount,
        boolean watched,
        long watchCount,
        boolean antiSnipeEnabled,
        int antiSnipeWindowSeconds,
        int antiSnipeExtensionSeconds,
        List<BidDto> recentBids,
        /** Ordered gallery; empty when the lot has no photographs. Index 0 is the cover. */
        List<ItemImageDto> images
) {
}
