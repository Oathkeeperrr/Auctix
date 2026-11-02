package com.auctix.repository;

import com.auctix.domain.AutoBid;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AutoBidRepository extends JpaRepository<AutoBid, Long> {

    List<AutoBid> findByAuctionIdAndActiveTrueOrderByMaxAmountDescIdAsc(Long auctionId);

    Optional<AutoBid> findByAuctionIdAndBidderId(Long auctionId, Long bidderId);

    List<AutoBid> findByBidderIdAndActiveTrue(Long bidderId);
}
