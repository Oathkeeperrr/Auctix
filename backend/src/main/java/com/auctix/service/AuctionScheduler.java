package com.auctix.service;

import com.auctix.config.AuctixProperties;
import com.auctix.domain.Auction;
import com.auctix.domain.AuctionStatus;
import com.auctix.repository.AuctionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Drives every time-based transition from the <em>server</em> clock. Browsers only ever render a
 * countdown; they never decide that an auction has finished.
 *
 * <p>Candidates are selected without a lock and each one is then handed to
 * {@link AuctionLifecycleService}, which re-checks the condition while holding the row lock.
 * That keeps the scan cheap and still makes every transition safe against concurrent bids.</p>
 */
@Component
public class AuctionScheduler {

    private static final Logger log = LoggerFactory.getLogger(AuctionScheduler.class);

    private final AuctionRepository auctionRepository;
    private final AuctionLifecycleService lifecycleService;
    private final AuctixProperties properties;

    public AuctionScheduler(AuctionRepository auctionRepository,
                            AuctionLifecycleService lifecycleService,
                            AuctixProperties properties) {
        this.auctionRepository = auctionRepository;
        this.lifecycleService = lifecycleService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${auctix.auction.scheduler-interval-ms:1000}")
    public void tick() {
        Instant now = Instant.now();
        startDueAuctions(now);
        flagEndingAuctions(now);
        closeExpiredAuctions(now);
    }

    @Scheduled(cron = "${auctix.auction.archive-cron:0 15 3 * * *}")
    public void archiveOldAuctions() {
        Instant threshold = Instant.now().minus(properties.getAuction().getArchiveAfterDays(), ChronoUnit.DAYS);
        List<Auction> settled = auctionRepository.findByStatusAndClosedAtLessThanEqual(
                AuctionStatus.SETTLED, threshold);
        for (Auction auction : settled) {
            safely("archive", auction.getId(), () -> lifecycleService.archive(auction.getId()));
        }
    }

    private void startDueAuctions(Instant now) {
        for (Auction auction : auctionRepository.findByStatusAndStartTimeLessThanEqual(
                AuctionStatus.SCHEDULED, now)) {
            safely("start", auction.getId(), () -> lifecycleService.startIfDue(auction.getId()));
        }
    }

    private void flagEndingAuctions(Instant now) {
        Instant threshold = now.plusSeconds(properties.getAuction().getEndingWindowSeconds());
        for (Auction auction : auctionRepository.findByStatusAndEndTimeLessThanEqual(
                AuctionStatus.LIVE, threshold)) {
            safely("flag-ending", auction.getId(), () -> lifecycleService.flagEndingIfDue(auction.getId()));
        }
    }

    private void closeExpiredAuctions(Instant now) {
        for (Auction auction : auctionRepository.findByStatusInOrderByEndTimeAsc(
                List.of(AuctionStatus.LIVE, AuctionStatus.ENDING),
                org.springframework.data.domain.PageRequest.of(0, 200))) {
            if (!auction.hasExpired(now)) {
                break;
            }
            safely("close", auction.getId(), () -> lifecycleService.closeIfExpired(auction.getId()));
        }
    }

    private void safely(String what, Long auctionId, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            log.warn("Scheduler could not {} auction {}: {}", what, auctionId, ex.getMessage());
        }
    }
}
