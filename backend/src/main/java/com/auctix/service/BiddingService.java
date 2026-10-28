package com.auctix.service;

import com.auctix.config.AuctixProperties;
import com.auctix.domain.AuditAction;
import com.auctix.domain.NotificationType;
import com.auctix.dto.bid.BidDto;
import com.auctix.dto.bid.BidResultDto;
import com.auctix.dto.bid.PlaceBidRequest;
import com.auctix.dto.common.PageResponse;
import com.auctix.exception.ApiException;
import com.auctix.exception.ErrorCode;
import com.auctix.realtime.AuctionEvent;
import com.auctix.realtime.AuctionEventBus;
import com.auctix.realtime.AuctionEventType;
import com.auctix.repository.BidRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Orchestrates bid placement around {@link BidTransactionService}.
 *
 * <p>Responsibilities kept deliberately outside the locked transaction: rate limiting, bounded
 * retries when the database reports a lock conflict, and everything that happens
 * <em>after</em> commit — broadcasting over Redis/WebSocket, writing notifications and
 * recording the audit trail. Keeping these out of the critical section keeps the row lock
 * held for as short a time as possible, which is what lets many bidders be served per second.</p>
 */
@Service
public class BiddingService {

    private static final Logger log = LoggerFactory.getLogger(BiddingService.class);

    private final BidTransactionService bidTransactionService;
    private final BidRepository bidRepository;
    private final RateLimiterService rateLimiter;
    private final NotificationService notificationService;
    private final AuctionEventBus eventBus;
    private final AuditService auditService;
    private final AuctixProperties properties;

    public BiddingService(BidTransactionService bidTransactionService,
                          BidRepository bidRepository,
                          RateLimiterService rateLimiter,
                          NotificationService notificationService,
                          AuctionEventBus eventBus,
                          AuditService auditService,
                          AuctixProperties properties) {
        this.bidTransactionService = bidTransactionService;
        this.bidRepository = bidRepository;
        this.rateLimiter = rateLimiter;
        this.notificationService = notificationService;
        this.eventBus = eventBus;
        this.auditService = auditService;
        this.properties = properties;
    }

    public BidResultDto placeBid(Long auctionId, Long bidderId, String bidderEmail,
                                 PlaceBidRequest request, String sourceIp) {
        rateLimiter.checkBidAllowed(bidderId);

        BidOutcome outcome;
        try {
            outcome = executeWithRetries(auctionId, bidderId, request, sourceIp);
        } catch (ApiException ex) {
            auditService.record(bidderId, bidderEmail, AuditAction.BID_REJECTED, "Auction", auctionId,
                    ex.getCode().name() + ": " + ex.getMessage()
                            + " (attempted " + request.amount() + ")");
            eventBus.publish(new AuctionEvent(AuctionEventType.BID_REJECTED, auctionId,
                    java.time.Instant.now(), null, null, null, ex.getMessage(), bidderEmail));
            throw ex;
        }

        publishOutcome(outcome, bidderId, bidderEmail);

        if (outcome.winningBid() == null) {
            return new BidResultDto(true, "Your maximum bid was updated.", null, outcome.state());
        }
        return new BidResultDto(true, "Your bid was accepted.", outcome.winningBid(), outcome.state());
    }

    private BidOutcome executeWithRetries(Long auctionId, Long bidderId,
                                          PlaceBidRequest request, String sourceIp) {
        int maxAttempts = Math.max(1, properties.getBidding().getMaxRetries());
        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return bidTransactionService.execute(auctionId, bidderId, request, sourceIp);
            } catch (ConcurrencyFailureException ex) {
                // Covers optimistic (@Version), pessimistic and deadlock-retry failures.
                last = ex;
                log.debug("Lock conflict on auction {} (attempt {}/{}): {}",
                        auctionId, attempt, maxAttempts, ex.getMessage());
                sleepBackoff(attempt);
            }
        }
        log.warn("Giving up on auction {} after {} lock conflicts", auctionId, maxAttempts, last);
        throw new ApiException(ErrorCode.CONCURRENT_UPDATE,
                "Several bids landed at once. Please check the current price and try again.");
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(Math.min(20L * attempt, 100L));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** Fan-out after commit: live price for everyone, private alerts for those affected. */
    private void publishOutcome(BidOutcome outcome, Long bidderId, String bidderEmail) {
        for (BidDto bid : outcome.createdBids()) {
            eventBus.publish(AuctionEvent.broadcast(AuctionEventType.BID_PLACED, outcome.auctionId(),
                    outcome.state(), bid,
                    bid.automatic() ? "Automatic bid " + bid.amount() : "New bid " + bid.amount()));
            auditService.record(bid.bidderId(), bid.automatic() ? "proxy" : bidderEmail,
                    AuditAction.BID_PLACED, "Auction", outcome.auctionId(),
                    "bidId=" + bid.id() + " amount=" + bid.amount()
                            + " automatic=" + bid.automatic()
                            + " serverTimestamp=" + bid.serverTimestamp());
        }

        if (outcome.extended()) {
            eventBus.publish(AuctionEvent.broadcast(AuctionEventType.AUCTION_EXTENDED, outcome.auctionId(),
                    outcome.state(), null,
                    "Anti-sniping: auction extended by " + outcome.extensionSeconds() + " seconds."));
            auditService.recordSystem(AuditAction.AUCTION_EXTENDED, "Auction", outcome.auctionId(),
                    "Extended to " + outcome.state().endTime());
        }

        for (BidOutcome.OutbidTarget target : outcome.outbidTargets()) {
            notificationService.notify(target.userId(), NotificationType.OUTBID,
                    "You have been outbid",
                    "Your bid of " + target.previousAmount().toPlainString() + " on \""
                            + outcome.itemTitle() + "\" was beaten. The price is now "
                            + outcome.state().currentPrice().toPlainString() + ".",
                    outcome.auctionId(), outcome.state());
        }

        if (outcome.winningBid() != null && outcome.winningBid().bidderId().equals(bidderId)) {
            notificationService.notify(bidderId, NotificationType.BID_ACCEPTED,
                    "Your bid was accepted",
                    "You are the highest bidder on \"" + outcome.itemTitle() + "\" at "
                            + outcome.winningBid().amount().toPlainString() + ".",
                    outcome.auctionId(), outcome.state());
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<BidDto> history(Long auctionId, int page, int size) {
        return PageResponse.from(
                bidRepository.findByAuctionIdOrderByServerTimestampDescIdDesc(
                        auctionId, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))),
                BidDto::from);
    }

    @Transactional(readOnly = true)
    public List<BidDto> recentBids(Long auctionId) {
        return bidRepository.findTop25ByAuctionIdOrderByServerTimestampDescIdDesc(auctionId)
                .stream().map(BidDto::from).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<BidDto> myBids(Long userId, int page, int size) {
        return PageResponse.from(
                bidRepository.findByBidderIdOrderByServerTimestampDesc(
                        userId, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))),
                BidDto::from);
    }
}
