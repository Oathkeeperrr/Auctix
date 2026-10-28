package com.auctix.dto.bid;

import com.auctix.dto.auction.AuctionStateDto;

public record BidResultDto(
        boolean accepted,
        String message,
        BidDto bid,
        AuctionStateDto state
) {
}
