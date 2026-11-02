package com.auctix.repository;

import com.auctix.domain.Auction;
import com.auctix.domain.Bid;
import com.auctix.domain.BidStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

import java.time.Instant;

public interface BidRepository extends JpaRepository<Bid, Long> {

    Page<Bid> findByAuctionIdOrderByServerTimestampDescIdDesc(Long auctionId, Pageable pageable);

    List<Bid> findTop25ByAuctionIdOrderByServerTimestampDescIdDesc(Long auctionId);

    List<Bid> findByAuctionIdAndStatus(Long auctionId, BidStatus status);

    Optional<Bid> findTopByAuctionIdOrderByAmountDescIdDesc(Long auctionId);

    Page<Bid> findByBidderIdOrderByServerTimestampDesc(Long bidderId, Pageable pageable);

    long countByAuctionId(Long auctionId);

    long countByBidderId(Long bidderId);

    boolean existsByAuctionIdAndBidderId(Long auctionId, Long bidderId);

    @Query("select count(distinct b.bidder.id) from Bid b where b.auction.id = :auctionId")
    long countDistinctBidders(@Param("auctionId") Long auctionId);

    @Query("select distinct b.bidder from Bid b where b.auction.id = :auctionId")
    List<com.auctix.domain.User> findDistinctBidders(@Param("auctionId") Long auctionId);

    @Query("select distinct b.auction from Bid b where b.bidder.id = :bidderId order by b.auction.endTime asc")
    List<Auction> findDistinctAuctionsByBidder(@Param("bidderId") Long bidderId);

    @Query("select b from Bid b where b.auction.id = :auctionId and b.bidder.id = :bidderId order by b.amount desc")
    List<Bid> findBidderHistory(@Param("auctionId") Long auctionId, @Param("bidderId") Long bidderId);

    /** Raw bid times for the daily volume series; bucketed into days in Java. */
    @Query("select b.serverTimestamp from Bid b where b.serverTimestamp >= :since")
    List<Instant> timestampsSince(@Param("since") Instant since);
}
