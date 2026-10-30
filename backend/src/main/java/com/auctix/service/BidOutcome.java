package com.auctix.service;

import com.auctix.dto.auction.AuctionStateDto;
import com.auctix.dto.bid.BidDto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything the bid transaction produced, fully materialised into DTOs while the entities were
 * still attached. The orchestrating service publishes events and notifications from this
 * <em>after</em> the transaction commits, so no listener can observe an uncommitted bid and no
 * lazy association is touched outside the session.
 */
public record BidOutcome(
        BidDto winningBid,
        List<BidDto> createdBids,
        AuctionStateDto state,
        List<OutbidTarget> outbidTargets,
        boolean extended,
        int extensionSeconds,
        Long sellerId,
        String sellerEmail,
        String itemTitle,
        Long auctionId
) {

    /** A bidder who was leading at some point during this transaction and no longer is. */
    public record OutbidTarget(Long userId, String email, BigDecimal previousAmount) {
    }
}
