package com.auctix.service;

import com.auctix.domain.Auction;
import com.auctix.domain.User;
import com.auctix.domain.WatchlistEntry;
import com.auctix.dto.auction.AuctionSummaryDto;
import com.auctix.exception.ApiException;
import com.auctix.exception.ErrorCode;
import com.auctix.repository.AuctionRepository;
import com.auctix.repository.UserRepository;
import com.auctix.repository.WatchlistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class WatchlistService {

    private final WatchlistRepository watchlistRepository;
    private final AuctionRepository auctionRepository;
    private final UserRepository userRepository;

    public WatchlistService(WatchlistRepository watchlistRepository,
                            AuctionRepository auctionRepository,
                            UserRepository userRepository) {
        this.watchlistRepository = watchlistRepository;
        this.auctionRepository = auctionRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public boolean toggle(Long userId, Long auctionId) {
        if (watchlistRepository.existsByUserIdAndAuctionId(userId, auctionId)) {
            watchlistRepository.deleteEntry(userId, auctionId);
            return false;
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.USER_NOT_FOUND, userId));
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.AUCTION_NOT_FOUND, auctionId));
        watchlistRepository.save(new WatchlistEntry(user, auction));
        return true;
    }

    @Transactional(readOnly = true)
    public List<AuctionSummaryDto> list(Long userId) {
        Instant now = Instant.now();
        return watchlistRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(entry -> AuctionSummaryDto.from(entry.getAuction(), now, true,
                        watchlistRepository.countByAuctionId(entry.getAuction().getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public long size(Long userId) {
        return watchlistRepository.findWatchedAuctionIds(userId).size();
    }
}
