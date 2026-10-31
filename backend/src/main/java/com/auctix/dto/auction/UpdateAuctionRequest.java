package com.auctix.dto.auction;

import com.auctix.domain.Category;
import com.auctix.domain.ModelKind;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Partial update. Only auctions that have not started yet may be edited.
 */
public record UpdateAuctionRequest(
        @Size(min = 3, max = 160) String title,
        @Size(min = 10, max = 4000) String description,
        Category category,
        ModelKind modelKind,
        @Size(max = 500) String imageUrl,
        @Size(max = 120) String brand,
        @Size(max = 120) String conditionLabel,
        @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal startingPrice,
        @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal reservePrice,
        @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal minIncrement,
        Instant startTime,
        Instant endTime
) {
}
