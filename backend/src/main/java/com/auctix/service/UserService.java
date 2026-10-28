package com.auctix.service;

import com.auctix.domain.Auction;
import com.auctix.domain.AuctionStatus;
import com.auctix.dto.auction.AuctionSummaryDto;
import com.auctix.dto.auth.UserDto;
import com.auctix.dto.dashboard.DashboardStatsDto;
import com.auctix.exception.ApiException;
import com.auctix.exception.ErrorCode;
import com.auctix.repository.AuctionRepository;
import com.auctix.repository.BidRepository;
import com.auctix.repository.NotificationRepository;
import com.auctix.repository.UserRepository;
import com.auctix.repository.WatchlistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Read models for the bidder dashboard.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final BidRepository bidRepository;
    private final AuctionRepository auctionRepository;
    private final WatchlistRepository watchlistRepository;
    private final NotificationRepository notificationRepository;

    public UserService(UserRepository userRepository,
                       BidRepository bidRepository,
                       AuctionRepository auctionRepository,
                       WatchlistRepository watchlistRepository,
                       NotificationRepository notificationRepository) {
        this.userRepository = userRepository;
        this.bidRepository = bidRepository;
        this.auctionRepository = auctionRepository;
        this.watchlistRepository = watchlistRepository;
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public UserDto profile(Long userId) {
        return userRepository.findById(userId)
                .map(UserDto::from)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.USER_NOT_FOUND, userId));
    }

    @Transactional(readOnly = true)
    public DashboardStatsDto dashboardStats(Long userId) {
        List<Auction> participated = bidRepository.findDistinctAuctionsByBidder(userId);
        long active = participated.stream().filter(a -> a.getStatus().acceptsBids()).count();
        long won = participated.stream()
                .filter(a -> a.getWinner() != null && a.getWinner().getId().equals(userId))
                .count();
        long lost = participated.stream()
                .filter(a -> a.getStatus().isTerminal())
                .filter(a -> a.getWinner() == null || !a.getWinner().getId().equals(userId))
                .count();
        BigDecimal winnings = participated.stream()
                .filter(a -> a.getWinner() != null && a.getWinner().getId().equals(userId))
                .map(Auction::getWinningAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new DashboardStatsDto(
                active,
                won,
                lost,
                watchlistRepository.findWatchedAuctionIds(userId).size(),
                bidRepository.countByBidderId(userId),
                notificationRepository.countByUserIdAndReadFalse(userId),
                winnings);
    }

    @Transactional(readOnly = true)
    public List<AuctionSummaryDto> activeAuctions(Long userId) {
        Instant now = Instant.now();
        return bidRepository.findDistinctAuctionsByBidder(userId).stream()
                .filter(auction -> auction.getStatus().acceptsBids()
                        || auction.getStatus() == AuctionStatus.SCHEDULED)
                .map(auction -> toSummary(auction, userId, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuctionSummaryDto> wonAuctions(Long userId) {
        Instant now = Instant.now();
        return auctionRepository.findByWinnerIdOrderByClosedAtDesc(userId,
                        org.springframework.data.domain.PageRequest.of(0, 50))
                .getContent().stream()
                .map(auction -> toSummary(auction, userId, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuctionSummaryDto> lostAuctions(Long userId) {
        Instant now = Instant.now();
        return bidRepository.findDistinctAuctionsByBidder(userId).stream()
                .filter(auction -> auction.getStatus().isTerminal())
                .filter(auction -> auction.getWinner() == null
                        || !auction.getWinner().getId().equals(userId))
                .map(auction -> toSummary(auction, userId, now))
                .toList();
    }

    private AuctionSummaryDto toSummary(Auction auction, Long userId, Instant now) {
        return AuctionSummaryDto.from(auction, now,
                watchlistRepository.existsByUserIdAndAuctionId(userId, auction.getId()),
                watchlistRepository.countByAuctionId(auction.getId()));
    }
}
