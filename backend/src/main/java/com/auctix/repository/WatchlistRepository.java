package com.auctix.repository;

import com.auctix.domain.WatchlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WatchlistRepository extends JpaRepository<WatchlistEntry, Long> {

    boolean existsByUserIdAndAuctionId(Long userId, Long auctionId);

    Optional<WatchlistEntry> findByUserIdAndAuctionId(Long userId, Long auctionId);

    List<WatchlistEntry> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<WatchlistEntry> findByAuctionId(Long auctionId);

    long countByAuctionId(Long auctionId);

    @Modifying
    @Query("delete from WatchlistEntry w where w.user.id = :userId and w.auction.id = :auctionId")
    int deleteEntry(@Param("userId") Long userId, @Param("auctionId") Long auctionId);

    @Query("select w.auction.id from WatchlistEntry w where w.user.id = :userId")
    List<Long> findWatchedAuctionIds(@Param("userId") Long userId);
}
