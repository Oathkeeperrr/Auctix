package com.auctix.service;

import com.auctix.domain.*;
import com.auctix.dto.admin.AdminStatsDto;
import com.auctix.dto.admin.AdminUserDto;
import com.auctix.dto.admin.AuditEventDto;
import com.auctix.dto.auction.AuctionSummaryDto;
import com.auctix.dto.common.PageResponse;
import com.auctix.exception.ApiException;
import com.auctix.exception.ErrorCode;
import com.auctix.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final AuditEventRepository auditEventRepository;
    private final WatchlistRepository watchlistRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public AdminService(UserRepository userRepository,
                        AuctionRepository auctionRepository,
                        BidRepository bidRepository,
                        AuditEventRepository auditEventRepository,
                        WatchlistRepository watchlistRepository,
                        NotificationService notificationService,
                        AuditService auditService) {
        this.userRepository = userRepository;
        this.auctionRepository = auctionRepository;
        this.bidRepository = bidRepository;
        this.auditEventRepository = auditEventRepository;
        this.watchlistRepository = watchlistRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public AdminStatsDto stats() {
        BigDecimal settledValue = auctionRepository.totalSettledValue();
        return new AdminStatsDto(
                userRepository.count(),
                userRepository.countByRole(Role.BIDDER),
                userRepository.countByRole(Role.SELLER),
                userRepository.countByRole(Role.ADMIN),
                userRepository.countByStatus(UserStatus.SUSPENDED),
                auctionRepository.count(),
                auctionRepository.countByStatus(AuctionStatus.PENDING_APPROVAL),
                auctionRepository.countByStatus(AuctionStatus.LIVE)
                        + auctionRepository.countByStatus(AuctionStatus.ENDING),
                auctionRepository.countByStatus(AuctionStatus.SCHEDULED),
                auctionRepository.countByStatus(AuctionStatus.CLOSED),
                auctionRepository.countByStatus(AuctionStatus.SETTLED),
                bidRepository.count(),
                settledValue == null ? BigDecimal.ZERO : settledValue);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserDto> users(Role role, String query, int page, int size) {
        Specification<User> spec = Specification.where(roleIs(role)).and(matches(query));
        Page<User> result = userRepository.findAll(spec,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.from(result, user -> AdminUserDto.from(user,
                auctionRepository.countBySellerId(user.getId()),
                bidRepository.countByBidderId(user.getId())));
    }

    @Transactional
    public AdminUserDto updateStatus(Long userId, UserStatus status, String reason,
                                     Long adminId, String adminEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.USER_NOT_FOUND, userId));
        if (user.getRole() == Role.ADMIN && status == UserStatus.SUSPENDED) {
            throw new ApiException(ErrorCode.USER_NOT_AUTHORIZED, "Administrators cannot be suspended.");
        }
        user.setStatus(status);
        userRepository.save(user);

        auditService.record(adminId, adminEmail,
                status == UserStatus.SUSPENDED ? AuditAction.USER_SUSPENDED : AuditAction.USER_REINSTATED,
                "User", userId, reason);

        if (status == UserStatus.SUSPENDED) {
            notificationService.notify(userId, NotificationType.ACCOUNT_SUSPENDED,
                    "Your account was suspended",
                    reason == null || reason.isBlank() ? "Contact support for details." : reason, null);
        }
        return AdminUserDto.from(user,
                auctionRepository.countBySellerId(userId),
                bidRepository.countByBidderId(userId));
    }

    @Transactional
    public AdminUserDto updateRole(Long userId, Role role, Long adminId, String adminEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.USER_NOT_FOUND, userId));
        user.setRole(role);
        userRepository.save(user);
        auditService.record(adminId, adminEmail, AuditAction.USER_REINSTATED, "User", userId,
                "role changed to " + role);
        return AdminUserDto.from(user,
                auctionRepository.countBySellerId(userId),
                bidRepository.countByBidderId(userId));
    }

    @Transactional(readOnly = true)
    public PageResponse<AuctionSummaryDto> pendingApprovals(int page, int size) {
        Instant now = Instant.now();
        Page<Auction> result = auctionRepository.findByStatusOrderByCreatedAtAsc(
                AuctionStatus.PENDING_APPROVAL,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 60)));
        return PageResponse.from(result, auction -> AuctionSummaryDto.from(auction, now, false,
                watchlistRepository.countByAuctionId(auction.getId())));
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEventDto> auditTrail(AuditAction action, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200));
        Page<AuditEvent> result = action == null
                ? auditEventRepository.findAllByOrderByCreatedAtDesc(pageable)
                : auditEventRepository.findByActionOrderByCreatedAtDesc(action, pageable);
        return PageResponse.from(result, AuditEventDto::from);
    }

    @Transactional(readOnly = true)
    public List<Object[]> auditSummary() {
        return auditEventRepository.aggregateCountsByAction();
    }

    private Specification<User> roleIs(Role role) {
        if (role == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("role"), role);
    }

    private Specification<User> matches(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String pattern = "%" + text.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("fullName")), pattern),
                cb.like(cb.lower(root.get("email")), pattern));
    }
}
