package com.auctix.service;

import com.auctix.TestDataFactory;
import com.auctix.domain.*;
import com.auctix.dto.bid.PlaceBidRequest;
import com.auctix.exception.ApiException;
import com.auctix.exception.ErrorCode;
import com.auctix.repository.AuctionRepository;
import com.auctix.repository.BidRepository;
import com.auctix.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AuctionLifecycleTest {

    @Autowired
    private AuctionLifecycleService lifecycleService;
    @Autowired
    private BiddingService biddingService;
    @Autowired
    private AuctionRepository auctionRepository;
    @Autowired
    private BidRepository bidRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private TestDataFactory data;

    @Test
    @DisplayName("Approval moves a listing from PENDING_APPROVAL to SCHEDULED and notifies the seller")
    void approvalSchedulesTheAuction() {
        User admin = data.user("lifecycle-admin@auctix.io", Role.ADMIN);
        User seller = data.user("lifecycle-seller@auctix.io", Role.SELLER);
        Auction auction = data.pendingAuction(seller);

        lifecycleService.approve(auction.getId(), admin.getId(), admin.getEmail());

        Auction reloaded = auctionRepository.findById(auction.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AuctionStatus.SCHEDULED);
        assertThat(reloaded.getApprovedAt()).isNotNull();
        assertThat(notificationRepository.countByUserIdAndReadFalse(seller.getId())).isPositive();
    }

    @Test
    @DisplayName("Rejection records a reason and blocks bidding")
    void rejectionBlocksBidding() {
        User admin = data.user("reject-admin@auctix.io", Role.ADMIN);
        User seller = data.user("reject-seller@auctix.io", Role.SELLER);
        User bidder = data.user("reject-bidder@auctix.io", Role.BIDDER);
        Auction auction = data.pendingAuction(seller);

        lifecycleService.reject(auction.getId(), admin.getId(), admin.getEmail(), "Photos are unclear");

        Auction reloaded = auctionRepository.findById(auction.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AuctionStatus.REJECTED);
        assertThat(reloaded.getRejectionReason()).isEqualTo("Photos are unclear");

        assertThatThrownBy(() -> biddingService.placeBid(auction.getId(), bidder.getId(),
                bidder.getEmail(), new PlaceBidRequest(new BigDecimal("500.00"), null, null), "test"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.AUCTION_NOT_APPROVED);
    }

    @Test
    @DisplayName("An expired auction closes on server time, picks the highest bidder and settles")
    void closesOnServerTimeAndDeclaresWinner() {
        User seller = data.user("close-seller@auctix.io", Role.SELLER);
        User first = data.user("close-bidder-1@auctix.io", Role.BIDDER);
        User second = data.user("close-bidder-2@auctix.io", Role.BIDDER);

        Auction auction = data.liveAuction(seller, new BigDecimal("100.00"), new BigDecimal("10.00"),
                Instant.now().plus(2, ChronoUnit.HOURS));
        biddingService.placeBid(auction.getId(), first.getId(), first.getEmail(),
                new PlaceBidRequest(new BigDecimal("100.00"), null, null), "test");
        biddingService.placeBid(auction.getId(), second.getId(), second.getEmail(),
                new PlaceBidRequest(new BigDecimal("150.00"), null, null), "test");

        // Nothing closes while the end time is still ahead of the server clock.
        assertThat(lifecycleService.closeIfExpired(auction.getId())).isFalse();

        expire(auction.getId());
        assertThat(lifecycleService.closeIfExpired(auction.getId())).isTrue();

        Auction closed = auctionRepository.findById(auction.getId()).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(AuctionStatus.SETTLED);
        assertThat(closed.getWinner().getId()).isEqualTo(second.getId());
        assertThat(closed.getWinningAmount()).isEqualByComparingTo("150.00");
        assertThat(closed.getClosedAt()).isNotNull();

        List<Bid> won = bidRepository.findByAuctionIdAndStatus(auction.getId(), BidStatus.WON);
        assertThat(won).hasSize(1);
        assertThat(bidRepository.findByAuctionIdAndStatus(auction.getId(), BidStatus.LOST)).hasSize(1);

        // Bidding stops the moment the auction is closed.
        assertThatThrownBy(() -> biddingService.placeBid(auction.getId(), first.getId(),
                first.getEmail(), new PlaceBidRequest(new BigDecimal("500.00"), null, null), "test"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.AUCTION_ENDED);
    }

    @Test
    @DisplayName("An unmet reserve price means nobody wins")
    void reservePriceBlocksTheSale() {
        User seller = data.user("reserve-seller@auctix.io", Role.SELLER);
        User bidder = data.user("reserve-bidder@auctix.io", Role.BIDDER);

        Auction auction = data.liveAuction(seller, new BigDecimal("100.00"), new BigDecimal("10.00"));
        auction.setReservePrice(new BigDecimal("1000.00"));
        auctionRepository.save(auction);

        biddingService.placeBid(auction.getId(), bidder.getId(), bidder.getEmail(),
                new PlaceBidRequest(new BigDecimal("120.00"), null, null), "test");
        expire(auction.getId());
        lifecycleService.closeIfExpired(auction.getId());

        Auction closed = auctionRepository.findById(auction.getId()).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(AuctionStatus.SETTLED);
        assertThat(closed.getWinner()).isNull();
        assertThat(closed.isReserveMet()).isFalse();
    }

    @Test
    @DisplayName("Anti-sniping pushes the end time out when a bid lands in the final seconds")
    void antiSnipingExtendsTheAuction() {
        User seller = data.user("snipe-seller@auctix.io", Role.SELLER);
        User bidder = data.user("snipe-bidder@auctix.io", Role.BIDDER);

        Auction auction = data.liveAuction(seller, new BigDecimal("100.00"), new BigDecimal("10.00"),
                Instant.now().plusSeconds(15));
        Instant originalEnd = auction.getEndTime();

        biddingService.placeBid(auction.getId(), bidder.getId(), bidder.getEmail(),
                new PlaceBidRequest(new BigDecimal("100.00"), null, null), "test");

        Auction extended = auctionRepository.findById(auction.getId()).orElseThrow();
        assertThat(extended.getExtensionCount()).isEqualTo(1);
        assertThat(extended.getEndTime())
                .as("the end time moved out by the configured extension")
                .isAfter(originalEnd.plusSeconds(29))
                .isBefore(originalEnd.plusSeconds(31));
        assertThat(extended.getOriginalEndTime())
                .as("the original finish time is preserved for the audit trail")
                .isBefore(extended.getEndTime());
    }

    @Test
    @DisplayName("Proxy bidding counter-bids automatically up to the stored ceiling")
    void proxyBiddingDefendsTheCeiling() {
        User seller = data.user("proxy-seller@auctix.io", Role.SELLER);
        User defender = data.user("proxy-defender@auctix.io", Role.BIDDER);
        User challenger = data.user("proxy-challenger@auctix.io", Role.BIDDER);

        Auction auction = data.liveAuction(seller, new BigDecimal("100.00"), new BigDecimal("10.00"));

        // The defender is willing to go to 500 but only bids 100 right now.
        biddingService.placeBid(auction.getId(), defender.getId(), defender.getEmail(),
                new PlaceBidRequest(new BigDecimal("100.00"), new BigDecimal("500.00"), null), "test");

        // The challenger bids 200: the engine answers on the defender's behalf.
        biddingService.placeBid(auction.getId(), challenger.getId(), challenger.getEmail(),
                new PlaceBidRequest(new BigDecimal("200.00"), null, null), "test");

        Auction reloaded = auctionRepository.findById(auction.getId()).orElseThrow();
        assertThat(reloaded.getHighestBidder().getId()).isEqualTo(defender.getId());
        assertThat(reloaded.getCurrentPrice()).isEqualByComparingTo("210.00");

        List<Bid> automatic = bidRepository.findByAuctionIdOrderByServerTimestampDescIdDesc(
                        auction.getId(), PageRequest.of(0, 10))
                .getContent().stream().filter(Bid::isAutomatic).toList();
        assertThat(automatic).hasSize(1);
        assertThat(automatic.get(0).getBidder().getId()).isEqualTo(defender.getId());
    }

    @Test
    @DisplayName("A bid above a rival's proxy ceiling takes the lead outright")
    void outbiddingTheCeilingWins() {
        User seller = data.user("ceiling-seller@auctix.io", Role.SELLER);
        User defender = data.user("ceiling-defender@auctix.io", Role.BIDDER);
        User challenger = data.user("ceiling-challenger@auctix.io", Role.BIDDER);

        Auction auction = data.liveAuction(seller, new BigDecimal("100.00"), new BigDecimal("10.00"));
        biddingService.placeBid(auction.getId(), defender.getId(), defender.getEmail(),
                new PlaceBidRequest(new BigDecimal("100.00"), new BigDecimal("300.00"), null), "test");
        biddingService.placeBid(auction.getId(), challenger.getId(), challenger.getEmail(),
                new PlaceBidRequest(new BigDecimal("400.00"), null, null), "test");

        Auction reloaded = auctionRepository.findById(auction.getId()).orElseThrow();
        assertThat(reloaded.getHighestBidder().getId()).isEqualTo(challenger.getId());
        assertThat(reloaded.getCurrentPrice()).isEqualByComparingTo("400.00");
    }

    /** Moves the auction's end time into the past without going through the bidding engine. */
    private void expire(Long auctionId) {
        Auction auction = auctionRepository.findById(auctionId).orElseThrow();
        Instant past = Instant.now().minus(1, ChronoUnit.MINUTES);
        auction.setEndTime(past);
        auction.setOriginalEndTime(past);
        auctionRepository.saveAndFlush(auction);
    }
}
