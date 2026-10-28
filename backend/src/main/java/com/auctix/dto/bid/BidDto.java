package com.auctix.dto.bid;

import com.auctix.domain.Bid;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Bid history entry. The bidder's real name and e-mail are never exposed:
 * only a masked label such as {@code "Kal*** S."} plus the numeric id so a client
 * can highlight its own bids.
 */
public record BidDto(
        Long id,
        Long auctionId,
        Long bidderId,
        String bidderMasked,
        BigDecimal amount,
        boolean automatic,
        String status,
        Instant serverTimestamp
) {
    public static BidDto from(Bid bid) {
        return new BidDto(
                bid.getId(),
                bid.getAuction().getId(),
                bid.getBidder().getId(),
                bid.getBidder().maskedName(),
                bid.getAmount(),
                bid.isAutomatic(),
                bid.getStatus().name(),
                bid.getServerTimestamp());
    }
}
