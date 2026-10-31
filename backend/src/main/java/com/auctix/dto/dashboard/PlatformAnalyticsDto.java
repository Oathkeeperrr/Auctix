package com.auctix.dto.dashboard;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Everything the analytics screen draws, in one request.
 *
 * <p>Every figure is counted from real rows. Nothing here is sampled, projected or filled in:
 * a quiet day is a zero, and an empty platform produces empty series rather than invented
 * numbers.</p>
 *
 * @param days how many days the daily series cover, so the axis can be labelled honestly
 */
public record PlatformAnalyticsDto(
        Instant generatedAt,
        int days,
        List<DailyPoint> bidsPerDay,
        List<DailyPoint> auctionsPerDay,
        List<DailyPoint> signUpsPerDay,
        List<Slice> byCategory,
        List<Slice> byStatus,
        List<SellerRow> topSellers) {

    /** One day of a time series. {@code date} is ISO yyyy-MM-dd in UTC. */
    public record DailyPoint(String date, long count) {
    }

    /** One wedge or bar of a distribution. */
    public record Slice(String key, String label, long count) {
    }

    public record SellerRow(Long sellerId, String name, long lots, long settled, BigDecimal revenue) {
    }
}
