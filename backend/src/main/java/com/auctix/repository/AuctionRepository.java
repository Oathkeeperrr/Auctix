package com.auctix.repository;

import com.auctix.domain.Auction;
import com.auctix.domain.AuctionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AuctionRepository extends JpaRepository<Auction, Long>, JpaSpecificationExecutor<Auction> {

    /**
     * Loads the auction row with a database write lock (PostgreSQL {@code SELECT ... FOR UPDATE}).
     * Every concurrent bid on the same auction queues here, which is what makes bid ordering
     * deterministic and prevents lost updates.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Auction a where a.id = :id")
    Optional<Auction> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select a from Auction a
            join fetch a.item i
            join fetch a.seller s
            where a.id = :id
            """)
    Optional<Auction> findDetailById(@Param("id") Long id);

    List<Auction> findByStatusAndStartTimeLessThanEqual(AuctionStatus status, Instant now);

    List<Auction> findByStatusInAndEndTimeLessThanEqual(Collection<AuctionStatus> statuses, Instant now);

    List<Auction> findByStatusAndEndTimeLessThanEqual(AuctionStatus status, Instant threshold);

    List<Auction> findByStatusAndClosedAtLessThanEqual(AuctionStatus status, Instant threshold);

    Page<Auction> findBySellerIdOrderByCreatedAtDesc(Long sellerId, Pageable pageable);

    List<Auction> findBySellerIdAndStatusIn(Long sellerId, Collection<AuctionStatus> statuses);

    Page<Auction> findByStatusOrderByCreatedAtAsc(AuctionStatus status, Pageable pageable);

    List<Auction> findByStatusInOrderByEndTimeAsc(Collection<AuctionStatus> statuses, Pageable pageable);

    long countByStatus(AuctionStatus status);

    // ---------------------------------------------------------------- analytics
    // Timestamps are returned raw and bucketed into days in Java rather than grouped with a
    // database date function. Those functions differ between PostgreSQL and the H2 the tests
    // use, and at this scale the difference in cost is not measurable.

    @Query("select a.createdAt from Auction a where a.createdAt >= :since")
    List<Instant> createdAtSince(@Param("since") Instant since);

    @Query("select a.item.category, count(a) from Auction a group by a.item.category")
    List<Object[]> countGroupedByCategory();

    @Query("select a.status, count(a) from Auction a group by a.status")
    List<Object[]> countGroupedByStatus();

    @Query("select a.seller.id, a.seller.fullName, count(a) from Auction a "
            + "group by a.seller.id, a.seller.fullName order by count(a) desc")
    List<Object[]> sellerLotCounts(Pageable pageable);

    /** Settled value per seller. Null winning amounts are excluded rather than counted as zero. */
    @Query("select a.seller.id, count(a), sum(a.winningAmount) from Auction a "
            + "where a.status = :status and a.winningAmount is not null group by a.seller.id")
    List<Object[]> sellerSettledTotals(@Param("status") AuctionStatus status);

    long countBySellerId(Long sellerId);

    long countBySellerIdAndStatus(Long sellerId, AuctionStatus status);

    @Query("select coalesce(sum(a.winningAmount), 0) from Auction a where a.seller.id = :sellerId and a.winner is not null")
    java.math.BigDecimal totalRevenueForSeller(@Param("sellerId") Long sellerId);

    @Query("select coalesce(sum(a.winningAmount), 0) from Auction a where a.winner is not null")
    java.math.BigDecimal totalSettledValue();

    Page<Auction> findByWinnerIdOrderByClosedAtDesc(Long winnerId, Pageable pageable);
}
