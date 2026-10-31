package com.auctix.dto.assistant;

import com.auctix.domain.Auction;

import java.math.BigDecimal;

/** A lot the assistant mentioned, so the UI can link straight to it. */
public record AssistantReference(
        Long auctionId,
        String title,
        String categoryLabel,
        BigDecimal currentPrice,
        int bidCount,
        String status) {

    public static AssistantReference from(Auction auction) {
        return new AssistantReference(
                auction.getId(),
                auction.getItem().getTitle(),
                auction.getItem().getCategory().getLabel(),
                auction.getCurrentPrice(),
                auction.getBidCount(),
                auction.getStatus().name());
    }
}
