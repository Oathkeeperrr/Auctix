package com.auctix.domain;

/**
 * Platform roles. Spring Security authorities are the name prefixed with {@code ROLE_}
 * (ROLE_BIDDER, ROLE_SELLER, ROLE_ADMIN).
 */
public enum Role {
    BIDDER,
    SELLER,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
