package com.auctix.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A single accepted bid. Rejected attempts are not stored here; they are recorded as
 * {@link AuditEvent}s so the audit trail stays complete without polluting bid history.
 */
@Entity
@Table(name = "bids",
        indexes = {
                @Index(name = "idx_bids_auction", columnList = "auction_id"),
                @Index(name = "idx_bids_bidder", columnList = "bidder_id"),
                @Index(name = "idx_bids_server_ts", columnList = "server_timestamp")
        })
public class Bid {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "auction_id", nullable = false)
    private Auction auction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bidder_id", nullable = false)
    private User bidder;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    /** Ceiling for proxy bidding, when the bid was placed through an automatic bid. */
    @Column(name = "max_amount", precision = 15, scale = 2)
    private BigDecimal maxAmount;

    /** True when the bidding engine placed this bid on the user's behalf (proxy bidding). */
    @Column(name = "automatic", nullable = false)
    private boolean automatic = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BidStatus status = BidStatus.WINNING;

    /**
     * Authoritative timestamp assigned by the server inside the bid transaction.
     * Client clocks are never trusted.
     */
    @Column(name = "server_timestamp", nullable = false)
    private Instant serverTimestamp = Instant.now();

    @Column(name = "client_timestamp")
    private Instant clientTimestamp;

    @Column(name = "source_ip", length = 60)
    private String sourceIp;

    protected Bid() {
    }

    public Bid(Auction auction, User bidder, BigDecimal amount, Instant serverTimestamp) {
        this.auction = auction;
        this.bidder = bidder;
        this.amount = amount;
        this.serverTimestamp = serverTimestamp;
        this.status = BidStatus.WINNING;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Auction getAuction() {
        return auction;
    }

    public void setAuction(Auction auction) {
        this.auction = auction;
    }

    public User getBidder() {
        return bidder;
    }

    public void setBidder(User bidder) {
        this.bidder = bidder;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getMaxAmount() {
        return maxAmount;
    }

    public void setMaxAmount(BigDecimal maxAmount) {
        this.maxAmount = maxAmount;
    }

    public boolean isAutomatic() {
        return automatic;
    }

    public void setAutomatic(boolean automatic) {
        this.automatic = automatic;
    }

    public BidStatus getStatus() {
        return status;
    }

    public void setStatus(BidStatus status) {
        this.status = status;
    }

    public Instant getServerTimestamp() {
        return serverTimestamp;
    }

    public void setServerTimestamp(Instant serverTimestamp) {
        this.serverTimestamp = serverTimestamp;
    }

    public Instant getClientTimestamp() {
        return clientTimestamp;
    }

    public void setClientTimestamp(Instant clientTimestamp) {
        this.clientTimestamp = clientTimestamp;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public void setSourceIp(String sourceIp) {
        this.sourceIp = sourceIp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Bid other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
