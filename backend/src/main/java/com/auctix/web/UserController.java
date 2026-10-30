package com.auctix.web;

import com.auctix.dto.auction.AuctionSummaryDto;
import com.auctix.dto.auth.ChangePasswordRequest;
import com.auctix.dto.auth.UpdateProfileRequest;
import com.auctix.dto.auth.UserDto;
import com.auctix.dto.bid.BidDto;
import com.auctix.dto.common.MessageResponse;
import com.auctix.dto.common.PageResponse;
import com.auctix.dto.dashboard.DashboardStatsDto;
import com.auctix.security.UserPrincipal;
import com.auctix.service.AuthService;
import com.auctix.service.BiddingService;
import com.auctix.service.UserService;
import com.auctix.service.WatchlistService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users/me")
@Tag(name = "Bidder dashboard")
public class UserController {

    private final UserService userService;
    private final AuthService authService;
    private final BiddingService biddingService;
    private final WatchlistService watchlistService;

    public UserController(UserService userService,
                          AuthService authService,
                          BiddingService biddingService,
                          WatchlistService watchlistService) {
        this.userService = userService;
        this.authService = authService;
        this.biddingService = biddingService;
        this.watchlistService = watchlistService;
    }

    @GetMapping
    public UserDto me(@AuthenticationPrincipal UserPrincipal principal) {
        return userService.profile(CurrentUser.require(principal).getId());
    }

    @PutMapping
    public UserDto updateProfile(@Valid @RequestBody UpdateProfileRequest request,
                                 @AuthenticationPrincipal UserPrincipal principal) {
        return authService.updateProfile(CurrentUser.require(principal).getId(), request);
    }

    @PostMapping("/password")
    public MessageResponse changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                          @AuthenticationPrincipal UserPrincipal principal) {
        authService.changePassword(CurrentUser.require(principal).getId(), request);
        return new MessageResponse("Password updated.");
    }

    @GetMapping("/dashboard")
    public DashboardStatsDto dashboard(@AuthenticationPrincipal UserPrincipal principal) {
        return userService.dashboardStats(CurrentUser.require(principal).getId());
    }

    @GetMapping("/bids")
    public PageResponse<BidDto> bids(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "25") int size,
                                     @AuthenticationPrincipal UserPrincipal principal) {
        return biddingService.myBids(CurrentUser.require(principal).getId(), page, size);
    }

    @GetMapping("/active")
    public List<AuctionSummaryDto> active(@AuthenticationPrincipal UserPrincipal principal) {
        return userService.activeAuctions(CurrentUser.require(principal).getId());
    }

    @GetMapping("/wins")
    public List<AuctionSummaryDto> wins(@AuthenticationPrincipal UserPrincipal principal) {
        return userService.wonAuctions(CurrentUser.require(principal).getId());
    }

    @GetMapping("/losses")
    public List<AuctionSummaryDto> losses(@AuthenticationPrincipal UserPrincipal principal) {
        return userService.lostAuctions(CurrentUser.require(principal).getId());
    }

    @GetMapping("/watchlist")
    public List<AuctionSummaryDto> watchlist(@AuthenticationPrincipal UserPrincipal principal) {
        return watchlistService.list(CurrentUser.require(principal).getId());
    }
}
