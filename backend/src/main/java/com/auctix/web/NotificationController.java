package com.auctix.web;

import com.auctix.dto.common.PageResponse;
import com.auctix.dto.notification.NotificationDto;
import com.auctix.security.UserPrincipal;
import com.auctix.service.NotificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public PageResponse<NotificationDto> list(@RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        return notificationService.list(CurrentUser.require(principal).getId(), page, size);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal UserPrincipal principal) {
        return Map.of("unread", notificationService.unreadCount(CurrentUser.require(principal).getId()));
    }

    @PostMapping("/{id}/read")
    public NotificationDto markRead(@PathVariable Long id,
                                    @AuthenticationPrincipal UserPrincipal principal) {
        return notificationService.markRead(CurrentUser.require(principal).getId(), id);
    }

    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead(@AuthenticationPrincipal UserPrincipal principal) {
        return Map.of("updated", notificationService.markAllRead(CurrentUser.require(principal).getId()));
    }
}
