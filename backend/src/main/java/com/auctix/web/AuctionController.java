package com.auctix.web;

import com.auctix.domain.AuctionStatus;
import com.auctix.domain.Category;
import com.auctix.dto.auction.*;
import com.auctix.dto.bid.BidDto;
import com.auctix.dto.bid.BidResultDto;
import com.auctix.dto.bid.PlaceBidRequest;
import com.auctix.dto.common.MessageResponse;
import com.auctix.dto.common.PageResponse;
import com.auctix.security.UserPrincipal;
import com.auctix.service.AuctionService;
import com.auctix.service.BiddingService;
import com.auctix.service.WatchlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auctions")
@Tag(name = "Auctions")
public class AuctionController {

    private final AuctionService auctionService;
    private final BiddingService biddingService;
    private final WatchlistService watchlistService;

    public AuctionController(AuctionService auctionService,
                             BiddingService biddingService,
                             WatchlistService watchlistService) {
        this.auctionService = auctionService;
        this.biddingService = biddingService;
        this.watchlistService = watchlistService;
    }

    @Operation(summary = "Browse auctions with filters and sorting")
    @GetMapping
    public PageResponse<AuctionSummaryDto> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) AuctionStatus status,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false, defaultValue = "ENDING_SOON") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        AuctionSearchQuery query = new AuctionSearchQuery(search, category, status, minPrice, maxPrice,
                sort, page, size);
        return auctionService.search(query, CurrentUser.idOrNull(principal), false);
    }

    @Operation(summary = "Auctions for the landing page carousel")
    @GetMapping("/featured")
    public List<AuctionSummaryDto> featured(@RequestParam(defaultValue = "8") int limit,
                                            @AuthenticationPrincipal UserPrincipal principal) {
        return auctionService.featured(limit, CurrentUser.idOrNull(principal));
    }

    @GetMapping("/{id}")
    public AuctionDetailDto detail(@PathVariable Long id,
                                   @AuthenticationPrincipal UserPrincipal principal) {
        return auctionService.detail(id, CurrentUser.idOrNull(principal), CurrentUser.roleOrNull(principal));
    }

    @Operation(summary = "Create a listing. It enters PENDING_APPROVAL until an admin approves it.")
    @PostMapping
    public ResponseEntity<AuctionDetailDto> create(@Valid @RequestBody CreateAuctionRequest request,
                                                   @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal user = CurrentUser.require(principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(auctionService.create(user.getId(), request));
    }

    @PutMapping("/{id}")
    public AuctionDetailDto update(@PathVariable Long id,
                                   @Valid @RequestBody UpdateAuctionRequest request,
                                   @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal user = CurrentUser.require(principal);
        return auctionService.update(id, user.getId(), user.getRole(), request);
    }

    @Operation(summary = "Cancel an auction")
    @DeleteMapping("/{id}")
    public MessageResponse cancel(@PathVariable Long id,
                                  @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal user = CurrentUser.require(principal);
        auctionService.cancel(id, user.getId(), user.getRole());
        return new MessageResponse("Auction cancelled.");
    }

    // ------------------------------------------------------------------ bidding

    @Operation(summary = "Place a bid. All validation, ordering and timing is decided server-side.")
    @PostMapping("/{id}/bids")
    public BidResultDto placeBid(@PathVariable Long id,
                                 @Valid @RequestBody PlaceBidRequest request,
                                 @AuthenticationPrincipal UserPrincipal principal,
                                 HttpServletRequest httpRequest) {
        UserPrincipal user = CurrentUser.require(principal);
        return biddingService.placeBid(id, user.getId(), user.getEmail(), request, clientIp(httpRequest));
    }

    @GetMapping("/{id}/bids")
    public PageResponse<BidDto> bids(@PathVariable Long id,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "25") int size) {
        return biddingService.history(id, page, size);
    }

    // ------------------------------------------------------------------ watchlist

    @PostMapping("/{id}/watchlist")
    public Map<String, Object> toggleWatch(@PathVariable Long id,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal user = CurrentUser.require(principal);
        boolean watching = watchlistService.toggle(user.getId(), id);
        return Map.of("auctionId", id, "watched", watching);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
