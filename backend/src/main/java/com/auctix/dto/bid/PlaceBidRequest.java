package com.auctix.dto.bid;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public record PlaceBidRequest(
        @NotNull(message = "Bid amount is required")
        @DecimalMin(value = "0.01", message = "Bid must be greater than zero")
        @Digits(integer = 13, fraction = 2)
        BigDecimal amount,

        /** Optional proxy-bidding ceiling: the engine bids up to this automatically. */
        @DecimalMin(value = "0.01")
        @Digits(integer = 13, fraction = 2)
        BigDecimal maxAmount,

        /** Recorded for the audit trail only; never used for validation or ordering. */
        Instant clientTimestamp
) {
}
