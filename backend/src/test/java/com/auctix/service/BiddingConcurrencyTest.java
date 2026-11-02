package com.auctix.service;

import com.auctix.TestDataFactory;
import com.auctix.domain.*;
import com.auctix.dto.bid.PlaceBidRequest;
import com.auctix.exception.ApiException;
import com.auctix.exception.ErrorCode;
import com.auctix.repository.AuctionRepository;
import com.auctix.repository.BidRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The requirement that matters most: many people bidding at the same instant must leave the
 * database in a state that is both correct and explainable.
 */
@SpringBootTest
@ActiveProfiles("test")
class BiddingConcurrencyTest {

    @Autowired
    private BiddingService biddingService;
    @Autowired
    private AuctionRepository auctionRepository;
    @Autowired
    private BidRepository bidRepository;
    @Autowired
    private TestDataFactory data;

    @Test
    @DisplayName("20 bidders submitting simultaneously leave a consistent, strictly increasing ladder")
    void twentySimultaneousBiddersStayConsistent() throws Exception {
        User seller = data.user("concurrency-seller@auctix.io", Role.SELLER);
        Auction auction = data.liveAuction(seller, new BigDecimal("100.00"), new BigDecimal("10.00"));

        int bidders = 20;
        List<User> users = new ArrayList<>();
        for (int i = 0; i < bidders; i++) {
            users.add(data.user("racer" + i + "@auctix.io", Role.BIDDER));
        }

        ExecutorService pool = Executors.newFixedThreadPool(bidders);
        CountDownLatch ready = new CountDownLatch(bidders);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < bidders; i++) {
            User bidder = users.get(i);
            BigDecimal amount = new BigDecimal("100.00").add(BigDecimal.valueOf(i * 10L));
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    go.await(10, TimeUnit.SECONDS);
                    biddingService.placeBid(auction.getId(), bidder.getId(), bidder.getEmail(),
                            new PlaceBidRequest(amount, null, null), "test");
                    accepted.incrementAndGet();
                } catch (ApiException expected) {
                    // A bid that was valid when it was composed can be stale by the time the
                    // lock is granted. Rejecting it is the correct outcome.
                    rejected.incrementAndGet();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        for (Future<?> future : futures) {
            future.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        Auction reloaded = auctionRepository.findById(auction.getId()).orElseThrow();
        List<Bid> bids = bidRepository.findByAuctionIdOrderByServerTimestampDescIdDesc(
                        auction.getId(), org.springframework.data.domain.PageRequest.of(0, 100))
                .getContent();

        assertThat(accepted.get() + rejected.get()).isEqualTo(bidders);
        assertThat(accepted.get()).isPositive();

        // Nothing was lost and nothing was invented.
        assertThat(bids).hasSize(accepted.get());
        assertThat(reloaded.getBidCount()).isEqualTo(accepted.get());

        // The auction price is exactly the highest accepted bid.
        BigDecimal highest = bids.stream().map(Bid::getAmount)
                .max(BigDecimal::compareTo).orElseThrow();
        assertThat(reloaded.getCurrentPrice()).isEqualByComparingTo(highest);

        // Exactly one winner, and it is the highest bid.
        List<Bid> winning = bidRepository.findByAuctionIdAndStatus(auction.getId(), BidStatus.WINNING);
        assertThat(winning).hasSize(1);
        assertThat(winning.get(0).getAmount()).isEqualByComparingTo(highest);
        assertThat(reloaded.getHighestBidder().getId()).isEqualTo(winning.get(0).getBidder().getId());

        // Accepted bids form a strictly increasing ladder honouring the minimum increment.
        List<Bid> chronological = new ArrayList<>(bids);
        chronological.sort(java.util.Comparator.comparing(Bid::getId));
        BigDecimal previous = null;
        for (Bid bid : chronological) {
            if (previous != null) {
                assertThat(bid.getAmount())
                        .as("each accepted bid clears the previous one by the minimum increment")
                        .isGreaterThanOrEqualTo(previous.add(auction.getMinIncrement()));
            } else {
                assertThat(bid.getAmount()).isGreaterThanOrEqualTo(auction.getStartingPrice());
            }
            previous = bid.getAmount();
        }
    }

    @Test
    @DisplayName("When everyone bids the same amount at once, exactly one wins")
    void identicalSimultaneousBidsProduceOneWinner() throws Exception {
        User seller = data.user("tie-seller@auctix.io", Role.SELLER);
        Auction auction = data.liveAuction(seller, new BigDecimal("500.00"), new BigDecimal("25.00"));

        int bidders = 12;
        List<User> users = new ArrayList<>();
        for (int i = 0; i < bidders; i++) {
            users.add(data.user("tie" + i + "@auctix.io", Role.BIDDER));
        }

        ExecutorService pool = Executors.newFixedThreadPool(bidders);
        CyclicBarrier barrier = new CyclicBarrier(bidders);
        AtomicInteger accepted = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (User bidder : users) {
            futures.add(pool.submit(() -> {
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                    biddingService.placeBid(auction.getId(), bidder.getId(), bidder.getEmail(),
                            new PlaceBidRequest(new BigDecimal("500.00"), null, null), "test");
                    accepted.incrementAndGet();
                } catch (ApiException ignored) {
                    // Expected for everyone but the first.
                } catch (Exception interrupted) {
                    Thread.currentThread().interrupt();
                }
            }));
        }
        for (Future<?> future : futures) {
            future.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(accepted.get())
                .as("the database, not arrival order, decides the single winner")
                .isEqualTo(1);
        assertThat(auctionRepository.findById(auction.getId()).orElseThrow().getBidCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("A bid below the required minimum is rejected with BID_TOO_LOW")
    void rejectsBidsBelowMinimum() {
        User seller = data.user("low-seller@auctix.io", Role.SELLER);
        User bidder = data.user("low-bidder@auctix.io", Role.BIDDER);
        Auction auction = data.liveAuction(seller, new BigDecimal("200.00"), new BigDecimal("20.00"));

        assertThatThrownBy(() -> biddingService.placeBid(auction.getId(), bidder.getId(),
                bidder.getEmail(), new PlaceBidRequest(new BigDecimal("199.99"), null, null), "test"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.BID_TOO_LOW);
    }

    @Test
    @DisplayName("A seller cannot bid on their own auction and nobody can bid twice in a row")
    void rejectsSelfBidsAndDuplicateLeadership() {
        User seller = data.user("self-seller@auctix.io", Role.SELLER);
        User bidder = data.user("self-bidder@auctix.io", Role.BIDDER);
        Auction auction = data.liveAuction(seller, new BigDecimal("50.00"), new BigDecimal("5.00"));

        assertThatThrownBy(() -> biddingService.placeBid(auction.getId(), seller.getId(),
                seller.getEmail(), new PlaceBidRequest(new BigDecimal("60.00"), null, null), "test"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.SELF_BID_FORBIDDEN);

        biddingService.placeBid(auction.getId(), bidder.getId(), bidder.getEmail(),
                new PlaceBidRequest(new BigDecimal("50.00"), null, null), "test");

        assertThatThrownBy(() -> biddingService.placeBid(auction.getId(), bidder.getId(),
                bidder.getEmail(), new PlaceBidRequest(new BigDecimal("80.00"), null, null), "test"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.DUPLICATE_BID);
    }
}
