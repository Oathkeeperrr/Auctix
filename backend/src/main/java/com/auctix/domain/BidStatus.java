package com.auctix.domain;

public enum BidStatus {
    /** Accepted and currently the highest bid. */
    WINNING,
    /** Accepted but since surpassed by another bidder. */
    OUTBID,
    /** Accepted and the auction closed with this bid on top. */
    WON,
    /** Accepted, auction closed, another bid won. */
    LOST
}
