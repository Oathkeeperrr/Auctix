package com.auctix.service;

import com.auctix.domain.Notification;
import com.auctix.domain.NotificationType;
import com.auctix.domain.User;
import com.auctix.dto.auction.AuctionStateDto;
import com.auctix.dto.common.PageResponse;
import com.auctix.dto.notification.NotificationDto;
import com.auctix.exception.ApiException;
import com.auctix.exception.ErrorCode;
import com.auctix.realtime.AuctionEvent;
import com.auctix.realtime.AuctionEventBus;
import com.auctix.realtime.AuctionEventType;
import com.auctix.repository.NotificationRepository;
import com.auctix.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Persists notifications and pushes them to the owner's private STOMP queue in the same step,
 * so the bell icon updates without polling.
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final AuctionEventBus eventBus;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository,
                               AuctionEventBus eventBus) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.eventBus = eventBus;
    }

    @Transactional
    public NotificationDto notify(Long userId, NotificationType type, String title,
                                  String message, Long auctionId, AuctionStateDto state) {
        Optional<User> user = userRepository.findById(userId);
        if (user.isEmpty()) {
            return null;
        }
        Notification saved = notificationRepository.save(
                new Notification(user.get(), type, title, message, auctionId));
        NotificationDto dto = NotificationDto.from(saved);
        eventBus.publish(AuctionEvent.toUser(user.get().getEmail(),
                mapEventType(type), auctionId, dto, state));
        return dto;
    }

    @Transactional
    public NotificationDto notify(Long userId, NotificationType type, String title,
                                  String message, Long auctionId) {
        return notify(userId, type, title, message, auctionId, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> list(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return PageResponse.from(
                notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable),
                NotificationDto::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public NotificationDto markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOTIFICATION_NOT_FOUND));
        if (!notification.getUser().getId().equals(userId)) {
            throw new ApiException(ErrorCode.USER_NOT_AUTHORIZED);
        }
        notification.setRead(true);
        return NotificationDto.from(notificationRepository.save(notification));
    }

    @Transactional
    public int markAllRead(Long userId) {
        return notificationRepository.markAllRead(userId);
    }

    private AuctionEventType mapEventType(NotificationType type) {
        return switch (type) {
            case OUTBID -> AuctionEventType.OUTBID;
            case AUCTION_WON -> AuctionEventType.AUCTION_WON;
            case AUCTION_CLOSED, AUCTION_LOST, AUCTION_SOLD -> AuctionEventType.AUCTION_CLOSED;
            case AUCTION_EXTENDED -> AuctionEventType.AUCTION_EXTENDED;
            case AUCTION_ENDING -> AuctionEventType.AUCTION_ENDING;
            case AUCTION_STARTED -> AuctionEventType.AUCTION_STARTED;
            case LISTING_APPROVED -> AuctionEventType.AUCTION_APPROVED;
            case LISTING_REJECTED -> AuctionEventType.AUCTION_REJECTED;
            default -> AuctionEventType.NOTIFICATION;
        };
    }
}
