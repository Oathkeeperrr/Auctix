package com.auctix.repository;

import com.auctix.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class AuctionRepositoryTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private AuctionRepository auctionRepository;
    @Autowired
    private BidRepository bidRepository;

    private Auction persistAuction(AuctionStatus status, Instant start, Instant end) {
        User seller = userRepository.save(new User("Repo Seller",
                "repo-seller-" + System.nanoTime() + "@auctix.io", "hash", Role.SELLER));
        Item item = itemRepository.save(new Item(seller, "Repository Lot",
                "Persisted by the repository test.", Category.ELECTRONICS));
        Auction auction = new Auction(item, new BigDecimal("100.00"), new BigDecimal("10.00"), start, end);
        auction.setStatus(status);
        return auctionRepository.save(auction);
    }

    @Test
    @DisplayName("Scheduled auctions whose start time has passed are found")
    void findsAuctionsDueToStart() {
        persistAuction(AuctionStatus.SCHEDULED,
                Instant.now().minus(1, ChronoUnit.MINUTES), Instant.now().plus(1, ChronoUnit.HOURS));
        persistAuction(AuctionStatus.SCHEDULED,
                Instant.now().plus(2, ChronoUnit.HOURS), Instant.now().plus(4, ChronoUnit.HOURS));

        List<Auction> due = auctionRepository.findByStatusAndStartTimeLessThanEqual(
                AuctionStatus.SCHEDULED, Instant.now());

        assertThat(due).hasSize(1);
    }

    @Test
    @DisplayName("Expired live auctions are found for closing")
    void findsExpiredAuctions() {
        persistAuction(AuctionStatus.LIVE,
                Instant.now().minus(3, ChronoUnit.HOURS), Instant.now().minus(1, ChronoUnit.MINUTES));
        persistAuction(AuctionStatus.LIVE,
                Instant.now().minus(1, ChronoUnit.HOURS), Instant.now().plus(1, ChronoUnit.HOURS));

        List<Auction> expired = auctionRepository.findByStatusInOrderByEndTimeAsc(
                List.of(AuctionStatus.LIVE, AuctionStatus.ENDING), PageRequest.of(0, 50));

        assertThat(expired).isNotEmpty();
        assertThat(expired.get(0).hasExpired(Instant.now())).isTrue();
    }

    @Test
    @DisplayName("The write-locked read returns the row for update")
    void pessimisticReadReturnsRow() {
        Auction auction = persistAuction(AuctionStatus.LIVE,
                Instant.now().minus(1, ChronoUnit.HOURS), Instant.now().plus(1, ChronoUnit.HOURS));

        assertThat(auctionRepository.findByIdForUpdate(auction.getId())).isPresent();
    }

    @Test
    @DisplayName("Distinct bidders are counted, not bids")
    void countsDistinctBidders() {
        Auction auction = persistAuction(AuctionStatus.LIVE,
                Instant.now().minus(1, ChronoUnit.HOURS), Instant.now().plus(1, ChronoUnit.HOURS));
        User one = userRepository.save(new User("Bidder One", "repo-b1@auctix.io", "hash", Role.BIDDER));
        User two = userRepository.save(new User("Bidder Two", "repo-b2@auctix.io", "hash", Role.BIDDER));

        bidRepository.save(new Bid(auction, one, new BigDecimal("110.00"), Instant.now()));
        bidRepository.save(new Bid(auction, two, new BigDecimal("120.00"), Instant.now()));
        bidRepository.save(new Bid(auction, one, new BigDecimal("130.00"), Instant.now()));

        assertThat(bidRepository.countByAuctionId(auction.getId())).isEqualTo(3);
        assertThat(bidRepository.countDistinctBidders(auction.getId())).isEqualTo(2);
        assertThat(bidRepository.findDistinctBidders(auction.getId())).hasSize(2);
    }

    @Test
    @DisplayName("The next minimum bid follows the increment rule")
    void nextMinimumBidFollowsIncrement() {
        Auction auction = persistAuction(AuctionStatus.LIVE,
                Instant.now().minus(1, ChronoUnit.HOURS), Instant.now().plus(1, ChronoUnit.HOURS));

        assertThat(auction.nextMinimumBid()).isEqualByComparingTo("100.00");

        auction.setBidCount(1);
        auction.setCurrentPrice(new BigDecimal("150.00"));
        assertThat(auction.nextMinimumBid()).isEqualByComparingTo("160.00");
    }
}
