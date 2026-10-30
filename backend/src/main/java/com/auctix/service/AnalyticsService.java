package com.auctix.service;

import com.auctix.domain.AuctionStatus;
import com.auctix.domain.Category;
import com.auctix.dto.dashboard.PlatformAnalyticsDto;
import com.auctix.repository.AuctionRepository;
import com.auctix.repository.BidRepository;
import com.auctix.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Platform analytics, counted from real rows.
 *
 * <h2>No invented numbers</h2>
 * Every series and distribution here is a count or a sum over the database. A day with no
 * activity is a zero, not a gap and not a smoothed estimate, and an empty platform produces
 * empty distributions rather than placeholder figures. Nothing is projected forward.
 *
 * <h2>Why days are bucketed in Java</h2>
 * Grouping by day in the query would mean a database date function, and those differ between
 * the PostgreSQL this runs on and the H2 the tests use. The timestamps are fetched and bucketed
 * here instead: portable, and at this scale the cost is not measurable. A platform with
 * millions of bids would move this into a database-side aggregation or a rollup table.
 */
@Service
public class AnalyticsService {

    private static final int TOP_SELLERS = 6;

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final UserRepository userRepository;

    public AnalyticsService(AuctionRepository auctionRepository,
                            BidRepository bidRepository,
                            UserRepository userRepository) {
        this.auctionRepository = auctionRepository;
        this.bidRepository = bidRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public PlatformAnalyticsDto platform(int days) {
        int window = Math.min(Math.max(days, 7), 90);
        Instant since = Instant.now().minus(window, ChronoUnit.DAYS);

        return new PlatformAnalyticsDto(
                Instant.now(),
                window,
                daily(bidRepository.timestampsSince(since), since, window),
                daily(auctionRepository.createdAtSince(since), since, window),
                daily(userRepository.createdAtSince(since), since, window),
                categorySlices(),
                statusSlices(),
                topSellers());
    }

    // ---------------------------------------------------------------- series

    /**
     * Buckets timestamps into one point per day, including days with nothing in them.
     *
     * <p>Zero-filling matters: a line chart drawn only from days that had activity implies
     * activity was continuous, which would overstate a quiet week.</p>
     */
    private List<PlatformAnalyticsDto.DailyPoint> daily(List<Instant> timestamps, Instant since, int days) {
        Map<LocalDate, Long> counts = new HashMap<>();
        for (Instant at : timestamps) {
            if (at == null) {
                continue;
            }
            counts.merge(at.atZone(ZoneOffset.UTC).toLocalDate(), 1L, Long::sum);
        }
        LocalDate start = since.atZone(ZoneOffset.UTC).toLocalDate();
        List<PlatformAnalyticsDto.DailyPoint> points = new ArrayList<>(days + 1);
        for (int i = 0; i <= days; i++) {
            LocalDate day = start.plusDays(i);
            points.add(new PlatformAnalyticsDto.DailyPoint(day.toString(), counts.getOrDefault(day, 0L)));
        }
        return points;
    }

    // ---------------------------------------------------------------- distributions

    private List<PlatformAnalyticsDto.Slice> categorySlices() {
        List<PlatformAnalyticsDto.Slice> slices = new ArrayList<>();
        for (Object[] row : auctionRepository.countGroupedByCategory()) {
            Category category = (Category) row[0];
            slices.add(new PlatformAnalyticsDto.Slice(
                    category.name(), category.getLabel(), ((Number) row[1]).longValue()));
        }
        slices.sort((a, b) -> Long.compare(b.count(), a.count()));
        return slices;
    }

    private List<PlatformAnalyticsDto.Slice> statusSlices() {
        List<PlatformAnalyticsDto.Slice> slices = new ArrayList<>();
        for (Object[] row : auctionRepository.countGroupedByStatus()) {
            AuctionStatus status = (AuctionStatus) row[0];
            slices.add(new PlatformAnalyticsDto.Slice(
                    status.name(), humanise(status.name()), ((Number) row[1]).longValue()));
        }
        slices.sort((a, b) -> Long.compare(b.count(), a.count()));
        return slices;
    }

    private List<PlatformAnalyticsDto.SellerRow> topSellers() {
        // Settled totals first, so the lot counts can be annotated without a second pass.
        Map<Long, long[]> settledCounts = new HashMap<>();
        Map<Long, BigDecimal> revenue = new HashMap<>();
        for (Object[] row : auctionRepository.sellerSettledTotals(AuctionStatus.SETTLED)) {
            Long sellerId = ((Number) row[0]).longValue();
            settledCounts.put(sellerId, new long[]{((Number) row[1]).longValue()});
            revenue.put(sellerId, row[2] == null ? BigDecimal.ZERO : (BigDecimal) row[2]);
        }

        Map<Long, PlatformAnalyticsDto.SellerRow> ordered = new LinkedHashMap<>();
        for (Object[] row : auctionRepository.sellerLotCounts(PageRequest.of(0, TOP_SELLERS))) {
            Long sellerId = ((Number) row[0]).longValue();
            long settled = settledCounts.containsKey(sellerId) ? settledCounts.get(sellerId)[0] : 0L;
            ordered.put(sellerId, new PlatformAnalyticsDto.SellerRow(
                    sellerId,
                    String.valueOf(row[1]),
                    ((Number) row[2]).longValue(),
                    settled,
                    revenue.getOrDefault(sellerId, BigDecimal.ZERO)));
        }
        return new ArrayList<>(ordered.values());
    }

    private static String humanise(String constant) {
        String lower = constant.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
