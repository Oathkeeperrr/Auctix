package com.auctix.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * An auction for a single {@link Item}.
 *
 * <p>Concurrency: bid placement loads this row with {@code PESSIMISTIC_WRITE}
 * (PostgreSQL {@code SELECT ... FOR UPDATE}) so simultaneous bids on the same auction are
 * serialised by the database. The {@link #version} column adds optimistic-locking defence in
 * depth for any path that updates the auction without the pessimistic lock.</p>
 */
@Entity
@Table(name = "auctions",
        indexes = {
                @Index(name = "idx_auctions_status", columnList = "status"),
                @Index(name = "idx_auctions_end_time", columnList = "end_time"),
                @Index(name = "idx_auctions_start_time", columnList = "start_time"),
                @Index(name = "idx_auctions_seller", columnList = "seller_id")
        })
public class Auction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    /** Denormalised from {@code item.seller} so seller dashboards can query without a join. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @Column(name = "starting_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal startingPrice;

    /** Optional hidden floor. The item is not sold if the final price is below it. */
    @Column(name = "reserve_price", precision = 15, scale = 2)
    private BigDecimal reservePrice;

    @Column(name = "min_increment", nullable = false, precision = 15, scale = 2)
    private BigDecimal minIncrement = new BigDecimal("1.00");

    /** Highest accepted bid so far; equals {@code startingPrice} while there are no bids. */
    @Column(name = "current_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal currentPrice = BigDecimal.ZERO;

    @Column(name = "bid_count", nullable = false)
    private int bidCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "highest_bidder_id")
    private User highestBidder;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    /** End time before any anti-sniping extension was applied. */
    @Column(name = "original_end_time", nullable = false)
    private Instant originalEndTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AuctionStatus status = AuctionStatus.PENDING_APPROVAL;

    @Column(name = "extension_count", nullable = false)
    private int extensionCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_id")
    private User approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    private User winner;

    @Column(name = "winning_amount", precision = 15, scale = 2)
    private BigDecimal winningAmount;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Auction() {
    }

    public Auction(Item item, BigDecimal startingPrice, BigDecimal minIncrement,
                   Instant startTime, Instant endTime) {
        this.item = item;
        this.seller = item.getSeller();
        this.startingPrice = startingPrice;
        this.currentPrice = startingPrice;
        this.minIncrement = minIncrement;
        this.startTime = startTime;
        this.endTime = endTime;
        this.originalEndTime = endTime;
        this.status = AuctionStatus.PENDING_APPROVAL;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    /**
     * Smallest amount the next bid may be.
     * The first bid must simply meet the starting price; later bids must clear the
     * current price by at least the minimum increment.
     */
    public BigDecimal nextMinimumBid() {
        if (bidCount == 0) {
            return startingPrice;
        }
        return currentPrice.add(minIncrement);
    }

    public boolean isReserveMet() {
        return reservePrice == null || currentPrice.compareTo(reservePrice) >= 0;
    }

    public boolean acceptsBids() {
        return status.acceptsBids();
    }

    public boolean hasExpired(Instant serverNow) {
        return !serverNow.isBefore(endTime);
    }

    public long secondsRemaining(Instant serverNow) {
        long seconds = endTime.getEpochSecond() - serverNow.getEpochSecond();
        return Math.max(seconds, 0L);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public User getSeller() {
        return seller;
    }

    public void setSeller(User seller) {
        this.seller = seller;
    }

    public BigDecimal getStartingPrice() {
        return startingPrice;
    }

    public void setStartingPrice(BigDecimal startingPrice) {
        this.startingPrice = startingPrice;
    }

    public BigDecimal getReservePrice() {
        return reservePrice;
    }

    public void setReservePrice(BigDecimal reservePrice) {
        this.reservePrice = reservePrice;
    }

    public BigDecimal getMinIncrement() {
        return minIncrement;
    }

    public void setMinIncrement(BigDecimal minIncrement) {
        this.minIncrement = minIncrement;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    public int getBidCount() {
        return bidCount;
    }

    public void setBidCount(int bidCount) {
        this.bidCount = bidCount;
    }

    public User getHighestBidder() {
        return highestBidder;
    }

    public void setHighestBidder(User highestBidder) {
        this.highestBidder = highestBidder;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public Instant getOriginalEndTime() {
        return originalEndTime;
    }

    public void setOriginalEndTime(Instant originalEndTime) {
        this.originalEndTime = originalEndTime;
    }

    public AuctionStatus getStatus() {
        return status;
    }

    public void setStatus(AuctionStatus status) {
        this.status = status;
    }

    public int getExtensionCount() {
        return extensionCount;
    }

    public void setExtensionCount(int extensionCount) {
        this.extensionCount = extensionCount;
    }

    public User getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(User approvedBy) {
        this.approvedBy = approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public User getWinner() {
        return winner;
    }

    public void setWinner(User winner) {
        this.winner = winner;
    }

    public BigDecimal getWinningAmount() {
        return winningAmount;
    }

    public void setWinningAmount(BigDecimal winningAmount) {
        this.winningAmount = winningAmount;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public Instant getSettledAt() {
        return settledAt;
    }

    public void setSettledAt(Instant settledAt) {
        this.settledAt = settledAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Auction other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
