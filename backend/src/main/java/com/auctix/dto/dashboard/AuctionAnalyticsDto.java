package com.auctix.dto.dashboard;

import java.math.BigDecimal;
import java.util.List;

public record AuctionAnalyticsDto(
        Long auctionId,
        String title,
        String status,
        int bidCount,
        long distinctBidders,
        BigDecimal startingPrice,
        BigDecimal currentPrice,
        BigDecimal uplift,
        List<BidPoint> timeline
) {
    public record BidPoint(long epochSeconds, BigDecimal amount) {
    }
}
