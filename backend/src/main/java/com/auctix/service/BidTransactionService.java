package com.auctix.service;

import com.auctix.config.AuctixProperties;
import com.auctix.domain.*;
import com.auctix.dto.auction.AuctionStateDto;
import com.auctix.dto.bid.BidDto;
import com.auctix.dto.bid.PlaceBidRequest;
import com.auctix.exception.ApiException;
import com.auctix.exception.ErrorCode;
import com.auctix.repository.AuctionRepository;
import com.auctix.repository.AutoBidRepository;
import com.auctix.repository.BidRepository;
import com.auctix.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The transactional core of the bidding engine. Everything here happens inside one database
 * transaction that begins by taking a write lock on the auction row.
 *
 * <p><strong>Why this is race-free.</strong> {@code findByIdForUpdate} issues
 * {@code SELECT ... FOR UPDATE}. If users A, B and C submit bids within microseconds of each
 * other, PostgreSQL grants the row lock to exactly one of them; the other two block on the lock
 * until that transaction commits, then re-read the <em>committed</em> current price and are
 * validated against it. Ordering therefore comes from the database, never from arrival order at
 * the application, and a lower bid that was "in flight" can never overwrite a higher committed
 * one. The {@code @Version} column on {@link Auction} adds a second line of defence for any code
 * path that updates an auction without the pessimistic lock.</p>
 *
 * <p>The frontend is never consulted about validity: amount, increment, auction state and the
 * clock are all checked here, on the server.</p>
 */
@Service
public class BidTransactionService {

    private static final Logger log = LoggerFactory.getLogger(BidTransactionService.class);

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final AutoBidRepository autoBidRepository;
    private final UserRepository userRepository;
    private final AuctixProperties properties;

    public BidTransactionService(AuctionRepository auctionRepository,
                                 BidRepository bidRepository,
                                 AutoBidRepository autoBidRepository,
                                 UserRepository userRepository,
                                 AuctixProperties properties) {
        this.auctionRepository = auctionRepository;
        this.bidRepository = bidRepository;
        this.autoBidRepository = autoBidRepository;
        this.userRepository = userRepository;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public BidOutcome execute(Long auctionId, Long bidderId, PlaceBidRequest request, String sourceIp) {
        final Instant now = Instant.now();

        // 1. Lock the auction row. Concurrent bidders queue here.
        Auction auction = auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.AUCTION_NOT_FOUND, auctionId));

        User bidder = userRepository.findById(bidderId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.USER_NOT_FOUND, bidderId));

        // 2. Authorisation and state checks, all server-side.
        validateBidder(auction, bidder);
        validateAuctionAcceptsBids(auction, now);

        BigDecimal amount = normalise(request.amount());
        BigDecimal maxAmount = request.maxAmount() == null ? null : normalise(request.maxAmount());

        boolean alreadyLeading = auction.getHighestBidder() != null
                && auction.getHighestBidder().getId().equals(bidder.getId());
        if (alreadyLeading) {
            // Bidding against yourself is never allowed; raising your proxy ceiling is.
            return raiseCeilingOnly(auction, bidder, maxAmount, now);
        }

        validateAmount(auction, amount, maxAmount);

        // 3. Remember who was leading before anything changes.
        Map<Long, OutbidCandidate> leaders = new LinkedHashMap<>();
        rememberLeader(leaders, auction);

        // 4. Previous winning bids lose their crown.
        for (Bid previous : bidRepository.findByAuctionIdAndStatus(auctionId, BidStatus.WINNING)) {
            previous.setStatus(BidStatus.OUTBID);
        }

        // 5. Register / update the proxy-bidding ceiling when one was supplied.
        if (maxAmount != null) {
            upsertAutoBid(auction, bidder, maxAmount);
        }

        // 6. The manual bid itself.
        List<Bid> created = new ArrayList<>();
        Bid manualBid = new Bid(auction, bidder, amount, now);
        manualBid.setClientTimestamp(request.clientTimestamp());
        manualBid.setSourceIp(sourceIp);
        manualBid.setMaxAmount(maxAmount);
        applyLeadingBid(auction, manualBid);
        created.add(manualBid);

        // 7. Let standing proxy bids from other users answer back.
        resolveProxyBids(auction, created, leaders, now);

        // 8. Anti-sniping: a late bid pushes the finish line out.
        boolean extended = applyAntiSnipe(auction, now);

        // 9. Persist. saveAllAndFlush assigns ids before we build DTOs.
        Bid winning = created.get(created.size() - 1);
        for (Bid bid : created) {
            bid.setStatus(bid == winning ? BidStatus.WINNING : BidStatus.OUTBID);
        }
        List<Bid> persisted = bidRepository.saveAllAndFlush(created);
        auctionRepository.saveAndFlush(auction);

        Bid persistedWinner = persisted.get(persisted.size() - 1);
        long distinctBidders = bidRepository.countDistinctBidders(auctionId);
        AuctionStateDto state = AuctionStateDto.from(auction, distinctBidders, now);

        Long finalLeaderId = auction.getHighestBidder() == null ? null : auction.getHighestBidder().getId();
        List<BidOutcome.OutbidTarget> outbid = leaders.values().stream()
                .filter(candidate -> !candidate.userId.equals(finalLeaderId))
                .map(candidate -> new BidOutcome.OutbidTarget(candidate.userId, candidate.email, candidate.amount))
                .toList();

        log.debug("Auction {} accepted bid {} at {} ({} bids created, extended={})",
                auctionId, persistedWinner.getId(), auction.getCurrentPrice(), persisted.size(), extended);

        return new BidOutcome(
                BidDto.from(persistedWinner),
                persisted.stream().map(BidDto::from).toList(),
                state,
                outbid,
                extended,
                properties.getAuction().getAntiSnipe().getExtensionSeconds(),
                auction.getSeller().getId(),
                auction.getSeller().getEmail(),
                auction.getItem().getTitle(),
                auctionId);
    }

    // ---------------------------------------------------------------- validation

    private void validateBidder(Auction auction, User bidder) {
        if (!bidder.isActive()) {
            throw new ApiException(ErrorCode.ACCOUNT_SUSPENDED);
        }
        if (auction.getSeller().getId().equals(bidder.getId())) {
            throw new ApiException(ErrorCode.SELF_BID_FORBIDDEN);
        }
    }

    private void validateAuctionAcceptsBids(Auction auction, Instant now) {
        AuctionStatus status = auction.getStatus();
        if (status == AuctionStatus.PENDING_APPROVAL || status == AuctionStatus.REJECTED) {
            throw new ApiException(ErrorCode.AUCTION_NOT_APPROVED);
        }
        if (status == AuctionStatus.SCHEDULED) {
            throw new ApiException(ErrorCode.AUCTION_NOT_ACTIVE,
                    "Bidding opens when the auction starts.");
        }
        if (status == AuctionStatus.CLOSED || status == AuctionStatus.SETTLED
                || status == AuctionStatus.ARCHIVED) {
            throw new ApiException(ErrorCode.AUCTION_ENDED);
        }
        if (status == AuctionStatus.CANCELLED) {
            throw new ApiException(ErrorCode.AUCTION_NOT_ACTIVE, "This auction was cancelled.");
        }
        if (!status.acceptsBids()) {
            throw new ApiException(ErrorCode.AUCTION_NOT_ACTIVE);
        }
        // The server clock decides, never the browser.
        if (auction.hasExpired(now)) {
            throw new ApiException(ErrorCode.AUCTION_ENDED,
                    "This auction closed at " + auction.getEndTime() + ".");
        }
    }

    private void validateAmount(Auction auction, BigDecimal amount, BigDecimal maxAmount) {
        BigDecimal required = auction.nextMinimumBid();
        if (amount.compareTo(required) < 0) {
            throw new ApiException(ErrorCode.BID_TOO_LOW,
                    "Your bid must be at least " + required.toPlainString() + ".",
                    Map.of("minimumBid", required, "currentPrice", auction.getCurrentPrice(),
                            "minIncrement", auction.getMinIncrement()));
        }
        if (maxAmount != null && maxAmount.compareTo(amount) < 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Your maximum automatic bid cannot be lower than the bid you are placing.");
        }
    }

    /**
     * The caller already holds the top bid. Placing another bid would be bidding against
     * themselves, so the only meaningful action is to raise their proxy-bidding ceiling.
     */
    private BidOutcome raiseCeilingOnly(Auction auction, User bidder, BigDecimal maxAmount, Instant now) {
        if (maxAmount == null) {
            throw new ApiException(ErrorCode.DUPLICATE_BID,
                    "You are already the highest bidder on this auction.");
        }
        if (maxAmount.compareTo(auction.getCurrentPrice()) <= 0) {
            throw new ApiException(ErrorCode.BID_TOO_LOW,
                    "Your maximum must be higher than your current winning bid of "
                            + auction.getCurrentPrice().toPlainString() + ".",
                    Map.of("currentPrice", auction.getCurrentPrice()));
        }
        upsertAutoBid(auction, bidder, maxAmount);
        auctionRepository.saveAndFlush(auction);
        AuctionStateDto state = AuctionStateDto.from(auction,
                bidRepository.countDistinctBidders(auction.getId()), now);
        return new BidOutcome(null, List.of(), state, List.of(), false,
                properties.getAuction().getAntiSnipe().getExtensionSeconds(),
                auction.getSeller().getId(), auction.getSeller().getEmail(),
                auction.getItem().getTitle(), auction.getId());
    }

    // ---------------------------------------------------------------- engine

    private void applyLeadingBid(Auction auction, Bid bid) {
        auction.setCurrentPrice(bid.getAmount());
        auction.setHighestBidder(bid.getBidder());
        auction.setBidCount(auction.getBidCount() + 1);
        auction.setUpdatedAt(bid.getServerTimestamp());
    }

    private void upsertAutoBid(Auction auction, User bidder, BigDecimal maxAmount) {
        Optional<AutoBid> existing = autoBidRepository.findByAuctionIdAndBidderId(auction.getId(), bidder.getId());
        if (existing.isPresent()) {
            AutoBid autoBid = existing.get();
            autoBid.setMaxAmount(maxAmount);
            autoBid.setActive(true);
            autoBidRepository.save(autoBid);
        } else {
            autoBidRepository.save(new AutoBid(auction, bidder, maxAmount));
        }
    }

    /**
     * Proxy bidding. Any other bidder with a standing ceiling high enough to beat the current
     * price automatically counter-bids the smallest winning amount. The loop terminates because
     * every iteration raises the price by at least the minimum increment, and it is additionally
     * capped by {@code auctix.bidding.max-proxy-iterations}.
     */
    private void resolveProxyBids(Auction auction, List<Bid> created,
                                  Map<Long, OutbidCandidate> leaders, Instant now) {
        int maxIterations = properties.getBidding().getMaxProxyIterations();
        for (int iteration = 0; iteration < maxIterations; iteration++) {
            Long leaderId = auction.getHighestBidder() == null ? null : auction.getHighestBidder().getId();
            BigDecimal required = auction.nextMinimumBid();

            Optional<AutoBid> challenger = autoBidRepository
                    .findByAuctionIdAndActiveTrueOrderByMaxAmountDescIdAsc(auction.getId())
                    .stream()
                    .filter(AutoBid::isActive)
                    .filter(autoBid -> leaderId == null || !autoBid.getBidder().getId().equals(leaderId))
                    .filter(autoBid -> autoBid.getMaxAmount().compareTo(required) >= 0)
                    .findFirst();

            if (challenger.isEmpty()) {
                return;
            }

            rememberLeader(leaders, auction);

            AutoBid autoBid = challenger.get();
            Bid proxyBid = new Bid(auction, autoBid.getBidder(), required, now);
            proxyBid.setAutomatic(true);
            proxyBid.setMaxAmount(autoBid.getMaxAmount());
            applyLeadingBid(auction, proxyBid);
            created.add(proxyBid);
        }
        log.warn("Proxy bid resolution hit the iteration cap on auction {}", auction.getId());
    }

    /**
     * Extends the auction when a valid bid lands inside the anti-sniping window.
     *
     * @return true when the end time moved
     */
    private boolean applyAntiSnipe(Auction auction, Instant now) {
        AuctixProperties.AntiSnipe config = properties.getAuction().getAntiSnipe();
        if (!config.isEnabled() || auction.getExtensionCount() >= config.getMaxExtensions()) {
            return false;
        }
        long remaining = auction.getEndTime().getEpochSecond() - now.getEpochSecond();
        if (remaining > config.getWindowSeconds()) {
            return false;
        }
        auction.setEndTime(auction.getEndTime().plusSeconds(config.getExtensionSeconds()));
        auction.setExtensionCount(auction.getExtensionCount() + 1);
        if (auction.getStatus() == AuctionStatus.ENDING
                && auction.secondsRemaining(now) > properties.getAuction().getEndingWindowSeconds()) {
            auction.setStatus(AuctionStatus.LIVE);
        }
        return true;
    }

    private void rememberLeader(Map<Long, OutbidCandidate> leaders, Auction auction) {
        User leader = auction.getHighestBidder();
        if (leader == null) {
            return;
        }
        leaders.put(leader.getId(), new OutbidCandidate(leader.getId(), leader.getEmail(), auction.getCurrentPrice()));
    }

    private BigDecimal normalise(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private record OutbidCandidate(Long userId, String email, BigDecimal amount) {
    }
}
