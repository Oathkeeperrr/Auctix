package com.auctix.web;

import com.auctix.dto.auction.AuctionSummaryDto;
import com.auctix.dto.common.PageResponse;
import com.auctix.dto.dashboard.AuctionAnalyticsDto;
import com.auctix.dto.dashboard.SellerStatsDto;
import com.auctix.security.UserPrincipal;
import com.auctix.service.AuctionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/seller")
@Tag(name = "Seller dashboard")
public class SellerController {

    private final AuctionService auctionService;

    public SellerController(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    @GetMapping("/auctions")
    public PageResponse<AuctionSummaryDto> myAuctions(@RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size,
                                                      @AuthenticationPrincipal UserPrincipal principal) {
        return auctionService.bySeller(CurrentUser.require(principal).getId(), page, size);
    }

    @GetMapping("/stats")
    public SellerStatsDto stats(@AuthenticationPrincipal UserPrincipal principal) {
        return auctionService.sellerStats(CurrentUser.require(principal).getId());
    }

    @GetMapping("/auctions/{id}/analytics")
    public AuctionAnalyticsDto analytics(@PathVariable Long id,
                                         @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal user = CurrentUser.require(principal);
        return auctionService.analytics(id, user.getId(), user.getRole());
    }
}
