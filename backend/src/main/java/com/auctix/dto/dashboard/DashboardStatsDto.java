package com.auctix.dto.dashboard;

import java.math.BigDecimal;

public record DashboardStatsDto(
        long activeBids,
        long auctionsWon,
        long auctionsLost,
        long watchlistSize,
        long totalBids,
        long unreadNotifications,
        BigDecimal totalWinningValue
) {
}
