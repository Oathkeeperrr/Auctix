package com.auctix.service;

import com.auctix.config.AuctixProperties;
import com.auctix.domain.*;
import com.auctix.dto.auction.AuctionStateDto;
import com.auctix.exception.ApiException;
import com.auctix.exception.ErrorCode;
import com.auctix.realtime.AuctionEvent;
import com.auctix.realtime.AuctionEventType;
import com.auctix.repository.AuctionRepository;
import com.auctix.repository.AutoBidRepository;
import com.auctix.repository.BidRepository;
import com.auctix.repository.UserRepository;
import com.auctix.repository.WatchlistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Owns every auction state transition.
 *
 * <pre>
 * PENDING_APPROVAL --approve--&gt; SCHEDULED --start time--&gt; LIVE --near end--&gt; ENDING
 *                                                             \--end time--&gt; CLOSED --&gt; SETTLED --&gt; ARCHIVED
 * </pre>
 *
 * <p>Transitions driven by time are triggered by {@link AuctionScheduler} and always re-check the
 * condition while holding the auction's write lock, so a transition can never race with a bid
 * that is extending the auction at the same moment.</p>
 */
@Service
public class AuctionLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(AuctionLifecycleService.class);

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final AutoBidRepository autoBidRepository;
    private final UserRepository userRepository;
    private final WatchlistRepository watchlistRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final AuctixProperties properties;

    public AuctionLifecycleService(AuctionRepository auctionRepository,
                                   BidRepository bidRepository,
                                   AutoBidRepository autoBidRepository,
                                   UserRepository userRepository,
                                   WatchlistRepository watchlistRepository,
                                   NotificationService notificationService,
                                   AuditService auditService,
                                   ApplicationEventPublisher applicationEventPublisher,
                                   AuctixProperties properties) {
        this.auctionRepository = auctionRepository;
        this.bidRepository = bidRepository;
        this.autoBidRepository = autoBidRepository;
        this.userRepository = userRepository;
        this.watchlistRepository = watchlistRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.properties = properties;
    }

    // ---------------------------------------------------------------- moderation

    @Transactional
    public AuctionStateDto approve(Long auctionId, Long adminId, String adminEmail) {
        Auction auction = lock(auctionId);
        requireTransition(auction, AuctionStatus.SCHEDULED);

        auction.setStatus(AuctionStatus.SCHEDULED);
        auction.setApprovedAt(Instant.now());
        auction.setRejectionReason(null);
        userRepository.findById(adminId).ifPresent(auction::setApprovedBy);
        auctionRepository.save(auction);

        auditService.record(adminId, adminEmail, AuditAction.AUCTION_APPROVED, "Auction", auctionId, null);
        notificationService.notify(auction.getSeller().getId(), NotificationType.LISTING_APPROVED,
                "Your listing was approved",
                "\"" + auction.getItem().getTitle() + "\" is approved and will go live at "
                        + auction.getStartTime() + ".",
                auctionId);

        AuctionStateDto state = state(auction);
        emit(AuctionEvent.broadcast(AuctionEventType.AUCTION_APPROVED, auctionId, state, null,
                "Listing approved"));
        return state;
    }

    @Transactional
    public AuctionStateDto reject(Long auctionId, Long adminId, String adminEmail, String reason) {
        Auction auction = lock(auctionId);
        requireTransition(auction, AuctionStatus.REJECTED);

        auction.setStatus(AuctionStatus.REJECTED);
        auction.setRejectionReason(reason == null || reason.isBlank() ? "No reason provided" : reason.trim());
        auctionRepository.save(auction);

        auditService.record(adminId, adminEmail, AuditAction.AUCTION_REJECTED, "Auction", auctionId, reason);
        notificationService.notify(auction.getSeller().getId(), NotificationType.LISTING_REJECTED,
                "Your listing needs changes",
                "\"" + auction.getItem().getTitle() + "\" was not approved: " + auction.getRejectionReason(),
                auctionId);

        AuctionStateDto state = state(auction);
        emit(AuctionEvent.broadcast(AuctionEventType.AUCTION_REJECTED, auctionId, state, null, "Listing rejected"));
        return state;
    }

    @Transactional
    public void cancel(Auction detached, Long actorId) {
        Auction auction = lock(detached.getId());
        if (auction.getStatus().isTerminal()) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION);
        }
        auction.setStatus(AuctionStatus.CANCELLED);
        auction.setClosedAt(Instant.now());
        auctionRepository.save(auction);
        deactivateAutoBids(auction.getId());

        auditService.record(actorId, null, AuditAction.AUCTION_CANCELLED, "Auction", auction.getId(), null);
        for (User bidder : bidRepository.findDistinctBidders(auction.getId())) {
            notificationService.notify(bidder.getId(), NotificationType.AUCTION_CLOSED,
                    "Auction cancelled",
                    "\"" + auction.getItem().getTitle() + "\" was cancelled. No charges apply.",
                    auction.getId());
        }
        emit(AuctionEvent.broadcast(AuctionEventType.AUCTION_CLOSED, auction.getId(), state(auction), null,
                "Auction cancelled"));
    }

    // ---------------------------------------------------------------- time-driven transitions

    /** SCHEDULED to LIVE once the server clock reaches the start time. */
    @Transactional
    public boolean startIfDue(Long auctionId) {
        Auction auction = lock(auctionId);
        Instant now = Instant.now();
        if (auction.getStatus() != AuctionStatus.SCHEDULED || auction.getStartTime().isAfter(now)) {
            return false;
        }
        if (auction.hasExpired(now)) {
            // Start time and end time both passed while the platform was down.
            return closeInternal(auction, now);
        }
        auction.setStatus(AuctionStatus.LIVE);
        auctionRepository.save(auction);

        auditService.recordSystem(AuditAction.AUCTION_STARTED, "Auction", auctionId, null);
        AuctionStateDto state = state(auction);
        emit(AuctionEvent.broadcast(AuctionEventType.AUCTION_STARTED, auctionId, state, null,
                "Bidding is now open"));
        notifyWatchers(auction, NotificationType.AUCTION_STARTED, "Auction started",
                "\"" + auction.getItem().getTitle() + "\" is now live.");
        return true;
    }

    /** LIVE to ENDING when the auction enters its final window. */
    @Transactional
    public boolean flagEndingIfDue(Long auctionId) {
        Auction auction = lock(auctionId);
        Instant now = Instant.now();
        if (auction.getStatus() != AuctionStatus.LIVE) {
            return false;
        }
        long remaining = auction.secondsRemaining(now);
        if (remaining > properties.getAuction().getEndingWindowSeconds()) {
            return false;
        }
        auction.setStatus(AuctionStatus.ENDING);
        auctionRepository.save(auction);

        AuctionStateDto state = state(auction);
        emit(AuctionEvent.broadcast(AuctionEventType.AUCTION_ENDING, auctionId, state, null,
                "Closing in " + remaining + " seconds"));
        for (User bidder : bidRepository.findDistinctBidders(auctionId)) {
            notificationService.notify(bidder.getId(), NotificationType.AUCTION_ENDING,
                    "Auction ending soon",
                    "\"" + auction.getItem().getTitle() + "\" closes in about "
                            + Math.max(1, remaining / 60) + " minute(s).",
                    auctionId);
        }
        return true;
    }

    /**
     * Closes and settles an auction whose end time has passed. Re-checks under the lock because
     * an anti-sniping extension may have moved the end time after the scheduler selected it.
     */
    @Transactional
    public boolean closeIfExpired(Long auctionId) {
        Auction auction = lock(auctionId);
        Instant now = Instant.now();
        if (!auction.getStatus().acceptsBids()) {
            return false;
        }
        if (!auction.hasExpired(now)) {
            return false;
        }
        return closeInternal(auction, now);
    }

    private boolean closeInternal(Auction auction, Instant now) {
        Long auctionId = auction.getId();
        boolean sold = auction.getBidCount() > 0 && auction.isReserveMet() && auction.getHighestBidder() != null;

        auction.setStatus(AuctionStatus.CLOSED);
        auction.setClosedAt(now);
        if (sold) {
            auction.setWinner(auction.getHighestBidder());
            auction.setWinningAmount(auction.getCurrentPrice());
        }

        for (Bid bid : bidRepository.findByAuctionIdAndStatus(auctionId, BidStatus.WINNING)) {
            bid.setStatus(sold ? BidStatus.WON : BidStatus.LOST);
        }
        for (Bid bid : bidRepository.findByAuctionIdAndStatus(auctionId, BidStatus.OUTBID)) {
            bid.setStatus(BidStatus.LOST);
        }
        deactivateAutoBids(auctionId);

        // Settlement happens immediately: winner and seller are told who won for how much.
        auction.setStatus(AuctionStatus.SETTLED);
        auction.setSettledAt(now);
        auctionRepository.save(auction);

        auditService.recordSystem(AuditAction.AUCTION_CLOSED, "Auction", auctionId,
                sold ? "winner=" + auction.getWinner().getId() + " amount=" + auction.getWinningAmount()
                        : "no winner (no bids or reserve not met)");
        auditService.recordSystem(AuditAction.AUCTION_SETTLED, "Auction", auctionId, null);

        String title = auction.getItem().getTitle();
        AuctionStateDto state = state(auction);

        if (sold) {
            notificationService.notify(auction.getWinner().getId(), NotificationType.AUCTION_WON,
                    "You won the auction",
                    "You won \"" + title + "\" with a bid of "
                            + auction.getWinningAmount().toPlainString() + ".", auctionId, state);
            notificationService.notify(auction.getSeller().getId(), NotificationType.AUCTION_SOLD,
                    "Your item sold",
                    "\"" + title + "\" sold for " + auction.getWinningAmount().toPlainString() + ".",
                    auctionId, state);
            for (User bidder : bidRepository.findDistinctBidders(auctionId)) {
                if (!bidder.getId().equals(auction.getWinner().getId())) {
                    notificationService.notify(bidder.getId(), NotificationType.AUCTION_LOST,
                            "Auction ended",
                            "\"" + title + "\" closed at " + auction.getWinningAmount().toPlainString()
                                    + ". Better luck next time.", auctionId, state);
                }
            }
        } else {
            notificationService.notify(auction.getSeller().getId(), NotificationType.AUCTION_CLOSED,
                    "Auction closed without a sale",
                    auction.getBidCount() == 0
                            ? "\"" + title + "\" received no bids."
                            : "\"" + title + "\" did not reach its reserve price.",
                    auctionId, state);
        }

        emit(AuctionEvent.broadcast(AuctionEventType.AUCTION_CLOSED, auctionId, state, null,
                sold ? "Sold for " + auction.getWinningAmount().toPlainString() : "Closed without a sale"));
        emit(AuctionEvent.broadcast(AuctionEventType.AUCTION_SETTLED, auctionId, state, null, "Settled"));
        log.info("Auction {} closed and settled (sold={})", auctionId, sold);
        return true;
    }

    @Transactional
    public boolean archive(Long auctionId) {
        Auction auction = lock(auctionId);
        if (auction.getStatus() != AuctionStatus.SETTLED) {
            return false;
        }
        auction.setStatus(AuctionStatus.ARCHIVED);
        auctionRepository.save(auction);
        return true;
    }

    // ---------------------------------------------------------------- helpers

    private Auction lock(Long auctionId) {
        return auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.AUCTION_NOT_FOUND, auctionId));
    }

    private void requireTransition(Auction auction, AuctionStatus target) {
        if (!auction.getStatus().canTransitionTo(target)) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Cannot move an auction from " + auction.getStatus() + " to " + target + ".");
        }
    }

    private void deactivateAutoBids(Long auctionId) {
        List<AutoBid> autoBids = autoBidRepository.findByAuctionIdAndActiveTrueOrderByMaxAmountDescIdAsc(auctionId);
        autoBids.forEach(autoBid -> autoBid.setActive(false));
        autoBidRepository.saveAll(autoBids);
    }

    private void notifyWatchers(Auction auction, NotificationType type, String title, String message) {
        watchlistRepository.findByAuctionId(auction.getId()).forEach(entry ->
                notificationService.notify(entry.getUser().getId(), type, title, message, auction.getId()));
    }

    private AuctionStateDto state(Auction auction) {
        return AuctionStateDto.from(auction,
                bidRepository.countDistinctBidders(auction.getId()), Instant.now());
    }

    /**
     * Events are published through the Spring context and relayed to Redis/WebSocket only after
     * the transaction commits, so nobody can observe a state change that later rolls back.
     */
    private void emit(AuctionEvent event) {
        applicationEventPublisher.publishEvent(new AuctionDomainEvent(event));
    }

    /** Internal carrier so the relay can listen for the commit. */
    public record AuctionDomainEvent(AuctionEvent event) {
    }
}
