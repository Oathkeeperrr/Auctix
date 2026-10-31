package com.auctix.dto.auction;

import com.auctix.domain.Category;
import com.auctix.domain.ModelKind;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.time.Instant;

public record CreateAuctionRequest(
        @NotBlank @Size(min = 3, max = 160) String title,
        @NotBlank @Size(min = 10, max = 4000) String description,
        @NotNull Category category,
        ModelKind modelKind,
        @Size(max = 500) String imageUrl,
        @Size(max = 120) String brand,
        @Size(max = 120) String conditionLabel,

        @NotNull @DecimalMin(value = "0.01", message = "Starting price must be greater than zero")
        @Digits(integer = 13, fraction = 2) BigDecimal startingPrice,

        @DecimalMin(value = "0.00") @Digits(integer = 13, fraction = 2) BigDecimal reservePrice,

        @NotNull @DecimalMin(value = "0.01", message = "Minimum increment must be greater than zero")
        @Digits(integer = 13, fraction = 2) BigDecimal minIncrement,

        @NotNull @Future(message = "Start time must be in the future") Instant startTime,
        @NotNull @Future(message = "End time must be in the future") Instant endTime,

        /**
         * URLs returned by {@code POST /api/uploads/images}, in display order. Index 0 becomes
         * the catalogue cover. Server-relative only; an off-site URL is rejected, so a listing
         * cannot be used to point at someone else's server.
         */
        @Size(max = 6, message = "A listing may carry at most 6 images.")
        List<@NotBlank @Size(max = 500) String> imageUrls
) {
}
