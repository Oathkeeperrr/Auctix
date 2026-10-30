package com.auctix.service;

import com.auctix.domain.Auction;
import com.auctix.domain.AuctionStatus;
import com.auctix.domain.Category;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;

/**
 * Type-safe, composable filters for the auction browser. Using the Criteria API keeps every
 * filter optional without resorting to null-parameter JPQL, which PostgreSQL cannot always type.
 */
public final class AuctionSpecifications {

    /** Statuses a visitor is allowed to see in the public browser. */
    public static final List<AuctionStatus> PUBLIC_STATUSES = List.of(
            AuctionStatus.SCHEDULED,
            AuctionStatus.LIVE,
            AuctionStatus.ENDING,
            AuctionStatus.CLOSED,
            AuctionStatus.SETTLED);

    private AuctionSpecifications() {
    }

    public static Specification<Auction> publiclyVisible() {
        return (root, query, cb) -> root.get("status").in(PUBLIC_STATUSES);
    }

    public static Specification<Auction> hasStatus(AuctionStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Auction> hasAnyStatus(List<AuctionStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> root.get("status").in(statuses);
    }

    public static Specification<Auction> hasCategory(Category category) {
        if (category == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("item").get("category"), category);
    }

    public static Specification<Auction> priceAtLeast(BigDecimal min) {
        if (min == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("currentPrice"), min);
    }

    public static Specification<Auction> priceAtMost(BigDecimal max) {
        if (max == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("currentPrice"), max);
    }

    public static Specification<Auction> titleOrDescriptionContains(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String pattern = "%" + text.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("item").get("title")), pattern),
                cb.like(cb.lower(root.get("item").get("description")), pattern));
    }

    public static Specification<Auction> sellerIs(Long sellerId) {
        if (sellerId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("seller").get("id"), sellerId);
    }
}
