package com.auctix.dto.admin;

import java.math.BigDecimal;

public record AdminStatsDto(
        long totalUsers,
        long bidders,
        long sellers,
        long admins,
        long suspendedUsers,
        long totalAuctions,
        long pendingApprovals,
        long liveAuctions,
        long scheduledAuctions,
        long closedAuctions,
        long settledAuctions,
        long totalBids,
        BigDecimal settledValue
) {
}
