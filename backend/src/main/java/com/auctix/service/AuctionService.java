package com.auctix.service;

import com.auctix.config.AuctixProperties;
import com.auctix.domain.*;
import com.auctix.dto.auction.*;
import com.auctix.dto.bid.BidDto;
import com.auctix.dto.common.PageResponse;
import com.auctix.dto.dashboard.AuctionAnalyticsDto;
import com.auctix.dto.dashboard.SellerStatsDto;
import com.auctix.exception.ApiException;
import com.auctix.exception.ErrorCode;
import com.auctix.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Auction catalogue: creation, editing, moderation and every read model the UI needs.
 * Bid placement lives in {@link BiddingService}; state transitions driven by the clock live in
 * {@link AuctionLifecycleService}.
 */
@Service
public class AuctionService {

    private final AuctionRepository auctionRepository;
    private final ItemRepository itemRepository;
    private final UserRepository userRepository;
    private final BidRepository bidRepository;
    private final WatchlistRepository watchlistRepository;
    private final AuditService auditService;
    private final AuctionLifecycleService lifecycleService;
    private final AuctixProperties properties;
    private final ItemImageRepository itemImageRepository;

    public AuctionService(AuctionRepository auctionRepository,
                          ItemRepository itemRepository,
                          UserRepository userRepository,
                          BidRepository bidRepository,
                          WatchlistRepository watchlistRepository,
                          AuditService auditService,
                          AuctionLifecycleService lifecycleService,
                          AuctixProperties properties,
                          ItemImageRepository itemImageRepository) {
        this.auctionRepository = auctionRepository;
        this.itemRepository = itemRepository;
        this.userRepository = userRepository;
        this.bidRepository = bidRepository;
        this.watchlistRepository = watchlistRepository;
        this.auditService = auditService;
        this.lifecycleService = lifecycleService;
        this.itemImageRepository = itemImageRepository;
        this.properties = properties;
    }

    // ---------------------------------------------------------------- commands

    @Transactional
    public AuctionDetailDto create(Long sellerId, CreateAuctionRequest request) {
        User seller = userRepository.findById(sellerId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.USER_NOT_FOUND, sellerId));
        validateSchedule(request.startTime(), request.endTime());
        validatePricing(request.startingPrice(), request.reservePrice(), request.minIncrement());

        Item item = new Item(seller, request.title().trim(), request.description().trim(), request.category());
        item.setModelKind(request.modelKind() == null ? request.category().getDefaultModel() : request.modelKind());
        item.setImageUrl(blankToNull(request.imageUrl()));
        item.setBrand(blankToNull(request.brand()));
        item.setConditionLabel(blankToNull(request.conditionLabel()));
        attachImages(item, request.imageUrls());
        itemRepository.save(item);

        Auction auction = new Auction(item, request.startingPrice(), request.minIncrement(),
                request.startTime(), request.endTime());
        auction.setReservePrice(request.reservePrice());
        // Auction's @Version is initialised to 0L rather than null, so Spring Data reads a new
        // instance as an existing row and calls merge(), which returns a managed COPY and leaves
        // this one detached with a null id. Everything after this point must use the result.
        Auction saved = auctionRepository.save(auction);

        auditService.record(sellerId, seller.getEmail(), AuditAction.AUCTION_CREATED,
                "Auction", saved.getId(),
                "title=" + item.getTitle() + " start=" + request.startTime() + " end=" + request.endTime());

        return toDetail(saved, seller, true);
    }

    @Transactional
    public AuctionDetailDto update(Long auctionId, Long actorId, Role actorRole, UpdateAuctionRequest request) {
        Auction auction = loadAuction(auctionId);
        requireSellerOrAdmin(auction, actorId, actorRole);

        if (auction.getStatus() != AuctionStatus.PENDING_APPROVAL
                && auction.getStatus() != AuctionStatus.REJECTED
                && auction.getStatus() != AuctionStatus.SCHEDULED) {
            throw new ApiException(ErrorCode.AUCTION_ALREADY_STARTED);
        }
        if (auction.getBidCount() > 0) {
            throw new ApiException(ErrorCode.AUCTION_ALREADY_STARTED,
                    "This auction already has bids and can no longer be edited.");
        }

        Item item = auction.getItem();
        if (request.title() != null) {
            item.setTitle(request.title().trim());
        }
        if (request.description() != null) {
            item.setDescription(request.description().trim());
        }
        if (request.category() != null) {
            item.setCategory(request.category());
            if (request.modelKind() == null) {
                item.setModelKind(request.category().getDefaultModel());
            }
        }
        if (request.modelKind() != null) {
            item.setModelKind(request.modelKind());
        }
        if (request.imageUrl() != null) {
            item.setImageUrl(blankToNull(request.imageUrl()));
        }
        if (request.brand() != null) {
            item.setBrand(blankToNull(request.brand()));
        }
        if (request.conditionLabel() != null) {
            item.setConditionLabel(blankToNull(request.conditionLabel()));
        }

        if (request.startingPrice() != null) {
            auction.setStartingPrice(request.startingPrice());
            auction.setCurrentPrice(request.startingPrice());
        }
        if (request.reservePrice() != null) {
            auction.setReservePrice(request.reservePrice());
        }
        if (request.minIncrement() != null) {
            auction.setMinIncrement(request.minIncrement());
        }
        Instant start = request.startTime() == null ? auction.getStartTime() : request.startTime();
        Instant end = request.endTime() == null ? auction.getEndTime() : request.endTime();
        if (request.startTime() != null || request.endTime() != null) {
            validateSchedule(start, end);
            auction.setStartTime(start);
            auction.setEndTime(end);
            auction.setOriginalEndTime(end);
        }
        validatePricing(auction.getStartingPrice(), auction.getReservePrice(), auction.getMinIncrement());

        // Any edit sends the listing back through moderation.
        if (auction.getStatus() == AuctionStatus.SCHEDULED || auction.getStatus() == AuctionStatus.REJECTED) {
            auction.setStatus(AuctionStatus.PENDING_APPROVAL);
            auction.setRejectionReason(null);
        }

        itemRepository.save(item);
        auctionRepository.save(auction);
        auditService.record(actorId, null, AuditAction.AUCTION_UPDATED, "Auction", auctionId, "edited");
        return toDetail(auction, null, true);
    }

    @Transactional
    public void cancel(Long auctionId, Long actorId, Role actorRole) {
        Auction auction = loadAuction(auctionId);
        requireSellerOrAdmin(auction, actorId, actorRole);
        if (auction.getStatus().isTerminal()) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "This auction has already finished.");
        }
        if (auction.getBidCount() > 0 && actorRole != Role.ADMIN) {
            throw new ApiException(ErrorCode.USER_NOT_AUTHORIZED,
                    "An auction with bids can only be cancelled by an administrator.");
        }
        lifecycleService.cancel(auction, actorId);
    }

    // ---------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public PageResponse<AuctionSummaryDto> search(AuctionSearchQuery query, Long currentUserId, boolean admin) {
        Specification<Auction> spec = admin
                ? Specification.where(AuctionSpecifications.hasStatus(query.status()))
                : Specification.where(AuctionSpecifications.publiclyVisible())
                        .and(AuctionSpecifications.hasStatus(query.status()));

        spec = spec.and(AuctionSpecifications.hasCategory(query.category()))
                .and(AuctionSpecifications.priceAtLeast(query.minPrice()))
                .and(AuctionSpecifications.priceAtMost(query.maxPrice()))
                .and(AuctionSpecifications.titleOrDescriptionContains(query.search()));

        Pageable pageable = PageRequest.of(
                Math.max(query.page(), 0),
                Math.min(Math.max(query.size(), 1), 60),
                sortFor(query.sortOrDefault()));

        Page<Auction> page = auctionRepository.findAll(spec, pageable);
        return PageResponse.from(page, auction -> summary(auction, currentUserId));
    }

    @Transactional(readOnly = true)
    public List<AuctionSummaryDto> featured(int limit, Long currentUserId) {
        List<Auction> auctions = auctionRepository.findByStatusInOrderByEndTimeAsc(
                List.of(AuctionStatus.LIVE, AuctionStatus.ENDING, AuctionStatus.SCHEDULED),
                PageRequest.of(0, Math.min(Math.max(limit, 1), 24)));
        return auctions.stream().map(auction -> summary(auction, currentUserId)).toList();
    }

    @Transactional(readOnly = true)
    public AuctionDetailDto detail(Long auctionId, Long currentUserId, Role currentRole) {
        Auction auction = auctionRepository.findDetailById(auctionId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.AUCTION_NOT_FOUND, auctionId));
        boolean privileged = currentRole == Role.ADMIN
                || (currentUserId != null && currentUserId.equals(auction.getSeller().getId()));
        if (!AuctionSpecifications.PUBLIC_STATUSES.contains(auction.getStatus()) && !privileged) {
            throw ApiException.notFound(ErrorCode.AUCTION_NOT_FOUND, auctionId);
        }
        return toDetailFor(auction, currentUserId, privileged);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuctionSummaryDto> bySeller(Long sellerId, int page, int size) {
        Page<Auction> result = auctionRepository.findBySellerIdOrderByCreatedAtDesc(
                sellerId, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 60)));
        return PageResponse.from(result, auction -> summary(auction, sellerId));
    }

    @Transactional(readOnly = true)
    public SellerStatsDto sellerStats(Long sellerId) {
        long total = auctionRepository.countBySellerId(sellerId);
        long bidsReceived = auctionRepository.findBySellerIdAndStatusIn(sellerId,
                        List.of(AuctionStatus.values()))
                .stream().mapToLong(Auction::getBidCount).sum();
        BigDecimal revenue = auctionRepository.totalRevenueForSeller(sellerId);
        return new SellerStatsDto(
                total,
                auctionRepository.countBySellerIdAndStatus(sellerId, AuctionStatus.PENDING_APPROVAL),
                auctionRepository.countBySellerIdAndStatus(sellerId, AuctionStatus.SCHEDULED),
                auctionRepository.countBySellerIdAndStatus(sellerId, AuctionStatus.LIVE)
                        + auctionRepository.countBySellerIdAndStatus(sellerId, AuctionStatus.ENDING),
                auctionRepository.countBySellerIdAndStatus(sellerId, AuctionStatus.CLOSED),
                auctionRepository.countBySellerIdAndStatus(sellerId, AuctionStatus.SETTLED),
                auctionRepository.countBySellerIdAndStatus(sellerId, AuctionStatus.REJECTED),
                bidsReceived,
                revenue == null ? BigDecimal.ZERO : revenue);
    }

    @Transactional(readOnly = true)
    public AuctionAnalyticsDto analytics(Long auctionId, Long actorId, Role actorRole) {
        Auction auction = loadAuction(auctionId);
        requireSellerOrAdmin(auction, actorId, actorRole);
        List<AuctionAnalyticsDto.BidPoint> timeline = new ArrayList<>(
                bidRepository.findTop25ByAuctionIdOrderByServerTimestampDescIdDesc(auctionId).stream()
                        .map(bid -> new AuctionAnalyticsDto.BidPoint(
                                bid.getServerTimestamp().getEpochSecond(), bid.getAmount()))
                        .toList());
        java.util.Collections.reverse(timeline);
        BigDecimal uplift = auction.getCurrentPrice().subtract(auction.getStartingPrice());
        return new AuctionAnalyticsDto(
                auction.getId(),
                auction.getItem().getTitle(),
                auction.getStatus().name(),
                auction.getBidCount(),
                bidRepository.countDistinctBidders(auctionId),
                auction.getStartingPrice(),
                auction.getCurrentPrice(),
                uplift,
                timeline);
    }

    @Transactional(readOnly = true)
    public List<AuctionSummaryDto> summaries(Collection<Auction> auctions, Long currentUserId) {
        return auctions.stream().map(auction -> summary(auction, currentUserId)).toList();
    }

    @Transactional(readOnly = true)
    public Auction requireAuction(Long auctionId) {
        return loadAuction(auctionId);
    }

    // ---------------------------------------------------------------- mapping helpers

    public AuctionSummaryDto summary(Auction auction, Long currentUserId) {
        boolean watched = currentUserId != null
                && watchlistRepository.existsByUserIdAndAuctionId(currentUserId, auction.getId());
        return AuctionSummaryDto.from(auction, Instant.now(), watched,
                watchlistRepository.countByAuctionId(auction.getId()));
    }

    public AuctionDetailDto toDetail(Auction auction, User seller, boolean privileged) {
        Long viewerId = seller == null ? null : seller.getId();
        return toDetailFor(auction, viewerId, privileged);
    }

    private AuctionDetailDto toDetailFor(Auction auction, Long currentUserId, boolean privileged) {
        Instant now = Instant.now();
        Item item = auction.getItem();
        User seller = auction.getSeller();
        List<BidDto> recent = bidRepository.findTop25ByAuctionIdOrderByServerTimestampDescIdDesc(auction.getId())
                .stream().map(BidDto::from).toList();
        boolean watched = currentUserId != null
                && watchlistRepository.existsByUserIdAndAuctionId(currentUserId, auction.getId());
        AuctixProperties.AntiSnipe antiSnipe = properties.getAuction().getAntiSnipe();

        return new AuctionDetailDto(
                auction.getId(),
                item.getTitle(),
                item.getDescription(),
                item.getCategory().name(),
                item.getCategory().getLabel(),
                item.getModelKind().name(),
                item.getImageUrl(),
                item.getBrand(),
                item.getConditionLabel(),
                auction.getStatus().name(),
                auction.getStartingPrice(),
                auction.getCurrentPrice(),
                auction.nextMinimumBid(),
                auction.getMinIncrement(),
                privileged ? auction.getReservePrice() : null,
                auction.isReserveMet(),
                auction.getBidCount(),
                bidRepository.countDistinctBidders(auction.getId()),
                auction.getHighestBidder() == null ? null : auction.getHighestBidder().getId(),
                auction.getHighestBidder() == null ? null : auction.getHighestBidder().maskedName(),
                auction.getStartTime(),
                auction.getEndTime(),
                auction.getOriginalEndTime(),
                now,
                auction.secondsRemaining(now),
                auction.getExtensionCount(),
                seller.getId(),
                seller.getFullName(),
                seller.getCreatedAt(),
                auction.getWinner() == null ? null : auction.getWinner().maskedName(),
                auction.getWinningAmount(),
                watched,
                watchlistRepository.countByAuctionId(auction.getId()),
                antiSnipe.isEnabled(),
                antiSnipe.getWindowSeconds(),
                antiSnipe.getExtensionSeconds(),
                recent,
                galleryFor(item));
    }

    /**
     * Read through the repository rather than {@code item.getImages()} so that this works
     * whether or not the caller holds an open session. One indexed query per detail view.
     */
    private List<ItemImageDto> galleryFor(Item item) {
        return itemImageRepository.findByItemIdOrderBySortOrderAsc(item.getId())
                .stream()
                .map(ItemImageDto::from)
                .toList();
    }

    /**
     * Attaches uploaded photographs to a new listing, in the order the seller arranged them.
     *
     * <p>Only URLs this application serves are accepted. A seller could otherwise paste any
     * address into the request and have every visitor's browser fetch it, which turns the
     * catalogue into someone else's traffic and leaks a visitor's address to a third party.</p>
     */
    private void attachImages(Item item, List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return;
        }
        for (String url : urls) {
            if (!StorageService.isLocalImageUrl(url)) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "Images must be uploaded through AuctiX before a listing can use them.");
            }
            item.addImage(url.trim(), null);
        }
    }

    public Set<Long> watchedIds(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        return new HashSet<>(watchlistRepository.findWatchedAuctionIds(userId));
    }

    // ---------------------------------------------------------------- internals

    private Auction loadAuction(Long auctionId) {
        return auctionRepository.findById(auctionId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.AUCTION_NOT_FOUND, auctionId));
    }

    private void requireSellerOrAdmin(Auction auction, Long actorId, Role actorRole) {
        if (actorRole == Role.ADMIN) {
            return;
        }
        if (actorId == null || !auction.getSeller().getId().equals(actorId)) {
            throw new ApiException(ErrorCode.USER_NOT_AUTHORIZED,
                    "This auction belongs to another seller.");
        }
    }

    private void validateSchedule(Instant start, Instant end) {
        if (start == null || end == null) {
            throw new ApiException(ErrorCode.INVALID_SCHEDULE, "Start and end time are required.");
        }
        if (!end.isAfter(start)) {
            throw new ApiException(ErrorCode.INVALID_SCHEDULE, "End time must be after the start time.");
        }
        if (end.isBefore(Instant.now())) {
            throw new ApiException(ErrorCode.INVALID_SCHEDULE, "End time must be in the future.");
        }
        if (end.getEpochSecond() - start.getEpochSecond() < 60) {
            throw new ApiException(ErrorCode.INVALID_SCHEDULE, "An auction must run for at least one minute.");
        }
    }

    private void validatePricing(BigDecimal startingPrice, BigDecimal reservePrice, BigDecimal minIncrement) {
        if (startingPrice == null || startingPrice.signum() <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Starting price must be greater than zero.");
        }
        if (minIncrement == null || minIncrement.signum() <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Minimum increment must be greater than zero.");
        }
        if (reservePrice != null && reservePrice.signum() > 0
                && reservePrice.compareTo(startingPrice) < 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Reserve price cannot be lower than the starting price.");
        }
    }

    private Sort sortFor(String sort) {
        return switch (sort) {
            case AuctionSearchQuery.SORT_NEWEST -> Sort.by(Sort.Direction.DESC, "createdAt");
            case AuctionSearchQuery.SORT_MOST_BIDS -> Sort.by(Sort.Direction.DESC, "bidCount");
            case AuctionSearchQuery.SORT_PRICE_ASC -> Sort.by(Sort.Direction.ASC, "currentPrice");
            case AuctionSearchQuery.SORT_PRICE_DESC -> Sort.by(Sort.Direction.DESC, "currentPrice");
            default -> Sort.by(Sort.Direction.ASC, "endTime");
        };
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
