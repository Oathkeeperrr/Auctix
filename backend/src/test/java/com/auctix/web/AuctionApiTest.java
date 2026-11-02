package com.auctix.web;

import com.auctix.TestDataFactory;
import com.auctix.domain.Auction;
import com.auctix.domain.Category;
import com.auctix.domain.Role;
import com.auctix.domain.User;
import com.auctix.dto.auction.CreateAuctionRequest;
import com.auctix.dto.bid.PlaceBidRequest;
import com.auctix.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuctionApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private TestDataFactory data;

    private String tokenFor(User user) {
        return "Bearer " + jwtService.generateToken(user.getId(), user.getEmail(), user.getRole().name());
    }

    @Test
    @DisplayName("A seller can create a listing and it waits for moderation")
    void sellerCreatesPendingListing() throws Exception {
        User seller = data.user("api-seller@auctix.io", Role.SELLER);
        CreateAuctionRequest request = new CreateAuctionRequest(
                "Vintage Leica M3", "A 1957 Leica M3 double-stroke in collector condition.",
                Category.COLLECTIBLES, null, null, "Leica", "Excellent",
                new BigDecimal("1500.00"), new BigDecimal("2000.00"), new BigDecimal("50.00"),
                Instant.now().plus(1, ChronoUnit.HOURS),
                Instant.now().plus(3, ChronoUnit.DAYS),
                null);

        mockMvc.perform(post("/api/auctions")
                        .header("Authorization", tokenFor(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.title").value("Vintage Leica M3"))
                .andExpect(jsonPath("$.reservePrice").value(2000.00));
    }

    @Test
    @DisplayName("A bidder may not create a listing")
    void bidderCannotCreateListing() throws Exception {
        User bidder = data.user("api-bidder-noseller@auctix.io", Role.BIDDER);
        CreateAuctionRequest request = new CreateAuctionRequest(
                "Not allowed", "This listing should never be created by a bidder account.",
                Category.ART, null, null, null, null,
                new BigDecimal("10.00"), null, new BigDecimal("1.00"),
                Instant.now().plus(1, ChronoUnit.HOURS),
                Instant.now().plus(2, ChronoUnit.DAYS),
                null);

        mockMvc.perform(post("/api/auctions")
                        .header("Authorization", tokenFor(bidder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Bid history is public but masks the bidders' identities")
    void bidHistoryIsMasked() throws Exception {
        User seller = data.user("history-seller@auctix.io", Role.SELLER);
        User bidder = data.user("history-bidder@auctix.io", Role.BIDDER);
        Auction auction = data.liveAuction(seller, new BigDecimal("100.00"), new BigDecimal("10.00"));

        mockMvc.perform(post("/api/auctions/" + auction.getId() + "/bids")
                        .header("Authorization", tokenFor(bidder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PlaceBidRequest(new BigDecimal("100.00"), null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.state.currentPrice").value(100.00))
                .andExpect(jsonPath("$.state.nextMinimumBid").value(110.00));

        mockMvc.perform(get("/api/auctions/" + auction.getId() + "/bids"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].bidderMasked").isNotEmpty())
                .andExpect(jsonPath("$.content[0].bidderEmail").doesNotExist())
                .andExpect(jsonPath("$.content[0].amount").value(100.00));
    }

    @Test
    @DisplayName("Anonymous visitors cannot bid")
    void anonymousCannotBid() throws Exception {
        User seller = data.user("anon-seller@auctix.io", Role.SELLER);
        Auction auction = data.liveAuction(seller, new BigDecimal("100.00"), new BigDecimal("10.00"));

        mockMvc.perform(post("/api/auctions/" + auction.getId() + "/bids")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PlaceBidRequest(new BigDecimal("200.00"), null, null))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Only administrators reach the admin API")
    void adminApiIsRoleProtected() throws Exception {
        User bidder = data.user("not-admin@auctix.io", Role.BIDDER);
        User admin = data.user("real-admin@auctix.io", Role.ADMIN);

        mockMvc.perform(get("/api/admin/stats").header("Authorization", tokenFor(bidder)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/stats").header("Authorization", tokenFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").isNumber());
    }

    @Test
    @DisplayName("A missing auction returns AUCTION_NOT_FOUND")
    void missingAuctionReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/auctions/99999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUCTION_NOT_FOUND"));
    }
}
