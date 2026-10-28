package com.auctix.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Final hop: pushes an event to the connected STOMP clients of <em>this</em> instance.
 */
@Component
public class WebSocketBroadcaster {

    public static final String TOPIC_AUCTION_PREFIX = "/topics/auction/";
    public static final String TOPIC_GLOBAL = "/topics/auctions";
    public static final String USER_QUEUE = "/queue/notifications";

    private static final Logger log = LoggerFactory.getLogger(WebSocketBroadcaster.class);

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void dispatch(AuctionEvent event) {
        try {
            if (event.isPrivate()) {
                messagingTemplate.convertAndSendToUser(event.targetUser(), USER_QUEUE, event);
                return;
            }
            if (event.auctionId() != null) {
                messagingTemplate.convertAndSend(TOPIC_AUCTION_PREFIX + event.auctionId(), event);
            }
            messagingTemplate.convertAndSend(TOPIC_GLOBAL, event);
        } catch (RuntimeException ex) {
            log.warn("Failed to dispatch {} for auction {}: {}", event.type(), event.auctionId(), ex.getMessage());
        }
    }
}
