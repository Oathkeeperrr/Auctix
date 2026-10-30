package com.auctix.dto.dashboard;

import java.math.BigDecimal;

public record SellerStatsDto(
        long totalAuctions,
        long pendingApproval,
        long scheduled,
        long live,
        long closed,
        long settled,
        long rejected,
        long totalBidsReceived,
        BigDecimal totalRevenue
) {
}
