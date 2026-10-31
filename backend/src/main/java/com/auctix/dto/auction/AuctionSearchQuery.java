package com.auctix.dto.auction;

import com.auctix.domain.AuctionStatus;
import com.auctix.domain.Category;

import java.math.BigDecimal;

/**
 * Filters for the auction browser. All fields are optional.
 *
 * @param sort one of {@code ENDING_SOON, NEWEST, MOST_BIDS, PRICE_ASC, PRICE_DESC}
 */
public record AuctionSearchQuery(
        String search,
        Category category,
        AuctionStatus status,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        String sort,
        int page,
        int size
) {
    public static final String SORT_ENDING_SOON = "ENDING_SOON";
    public static final String SORT_NEWEST = "NEWEST";
    public static final String SORT_MOST_BIDS = "MOST_BIDS";
    public static final String SORT_PRICE_ASC = "PRICE_ASC";
    public static final String SORT_PRICE_DESC = "PRICE_DESC";

    public String sortOrDefault() {
        return sort == null || sort.isBlank() ? SORT_ENDING_SOON : sort.toUpperCase();
    }
}
