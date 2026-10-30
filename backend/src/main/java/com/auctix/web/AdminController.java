package com.auctix.web;

import com.auctix.domain.AuctionStatus;
import com.auctix.domain.AuditAction;
import com.auctix.domain.Role;
import com.auctix.dto.admin.*;
import com.auctix.dto.auction.AuctionSearchQuery;
import com.auctix.dto.auction.AuctionStateDto;
import com.auctix.dto.auction.AuctionSummaryDto;
import com.auctix.dto.common.PageResponse;
import com.auctix.security.UserPrincipal;
import com.auctix.dto.dashboard.PlatformAnalyticsDto;
import com.auctix.service.AdminService;
import com.auctix.service.AnalyticsService;
import com.auctix.service.AuctionLifecycleService;
import com.auctix.service.AuctionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administration")
public class AdminController {

    private final AdminService adminService;
    private final AnalyticsService analyticsService;
    private final AuctionService auctionService;
    private final AuctionLifecycleService lifecycleService;

    public AdminController(AdminService adminService,
                           AuctionService auctionService,
                           AuctionLifecycleService lifecycleService,
                           AnalyticsService analyticsService) {
        this.adminService = adminService;
        this.auctionService = auctionService;
        this.lifecycleService = lifecycleService;
        this.analyticsService = analyticsService;
    }

    @GetMapping("/stats")
    public AdminStatsDto stats() {
        return adminService.stats();
    }

    // ------------------------------------------------------------------ users

    @GetMapping("/users")
    public PageResponse<AdminUserDto> users(@RequestParam(required = false) Role role,
                                            @RequestParam(required = false) String query,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return adminService.users(role, query, page, size);
    }

    @Operation(summary = "Suspend or reinstate a user")
    @PatchMapping("/users/{id}/status")
    public AdminUserDto updateStatus(@PathVariable Long id,
                                     @Valid @RequestBody UpdateUserStatusRequest request,
                                     @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal admin = CurrentUser.require(principal);
        return adminService.updateStatus(id, request.status(), request.reason(),
                admin.getId(), admin.getEmail());
    }

    @PatchMapping("/users/{id}/role")
    public AdminUserDto updateRole(@PathVariable Long id,
                                   @Valid @RequestBody UpdateUserRoleRequest request,
                                   @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal admin = CurrentUser.require(principal);
        return adminService.updateRole(id, request.role(), admin.getId(), admin.getEmail());
    }

    // ------------------------------------------------------------------ auctions

    @GetMapping("/auctions")
    public PageResponse<AuctionSummaryDto> auctions(@RequestParam(required = false) AuctionStatus status,
                                                     @RequestParam(required = false) String search,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size,
                                                     @AuthenticationPrincipal UserPrincipal principal) {
        AuctionSearchQuery query = new AuctionSearchQuery(search, null, status, null, null,
                AuctionSearchQuery.SORT_NEWEST, page, size);
        return auctionService.search(query, CurrentUser.idOrNull(principal), true);
    }

    @GetMapping("/auctions/pending")
    public PageResponse<AuctionSummaryDto> pending(@RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return adminService.pendingApprovals(page, size);
    }

    @Operation(summary = "Approve a listing so it can go live at its start time")
    @PostMapping("/auctions/{id}/approve")
    public AuctionStateDto approve(@PathVariable Long id,
                                   @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal admin = CurrentUser.require(principal);
        return lifecycleService.approve(id, admin.getId(), admin.getEmail());
    }

    @PostMapping("/auctions/{id}/reject")
    public AuctionStateDto reject(@PathVariable Long id,
                                  @RequestBody(required = false) ModerationRequest request,
                                  @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal admin = CurrentUser.require(principal);
        return lifecycleService.reject(id, admin.getId(), admin.getEmail(),
                request == null ? null : request.reason());
    }

    @PostMapping("/auctions/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable Long id,
                                      @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal admin = CurrentUser.require(principal);
        auctionService.cancel(id, admin.getId(), admin.getRole());
        return Map.of("auctionId", id, "status", AuctionStatus.CANCELLED.name());
    }

    // ------------------------------------------------------------------ audit

    @GetMapping("/audit")
    public PageResponse<AuditEventDto> audit(@RequestParam(required = false) AuditAction action,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "50") int size) {
        return adminService.auditTrail(action, page, size);
    }

    @Operation(summary = "Counts per audit action, used by the reports screen")
    @GetMapping("/reports/activity")
    public List<Map<String, Object>> activityReport() {
        List<Object[]> rows = adminService.auditSummary();
        return rows.stream().map(row -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("action", String.valueOf(row[0]));
            entry.put("count", row[1]);
            return entry;
        }).toList();
    }

    /**
     * Everything the analytics screen draws, counted from real rows.
     *
     * @param days size of the daily window; clamped to 7..90 by the service
     */
    @GetMapping("/analytics")
    public PlatformAnalyticsDto analytics(@RequestParam(defaultValue = "30") int days) {
        return analyticsService.platform(days);
    }
}
