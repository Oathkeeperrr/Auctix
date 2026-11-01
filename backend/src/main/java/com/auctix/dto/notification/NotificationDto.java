package com.auctix.dto.notification;

import com.auctix.domain.Notification;

import java.time.Instant;

public record NotificationDto(
        Long id,
        String type,
        String title,
        String message,
        Long auctionId,
        boolean read,
        Instant createdAt
) {
    public static NotificationDto from(Notification notification) {
        return new NotificationDto(
                notification.getId(),
                notification.getType().name(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getAuctionId(),
                notification.isRead(),
                notification.getCreatedAt());
    }
}
