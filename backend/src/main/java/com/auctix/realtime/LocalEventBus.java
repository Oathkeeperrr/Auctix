package com.auctix.realtime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * In-process event bus used when Redis is disabled (single instance, tests).
 */
@Component
@ConditionalOnProperty(prefix = "auctix.redis", name = "enabled", havingValue = "false")
public class LocalEventBus implements AuctionEventBus {

    private final WebSocketBroadcaster broadcaster;

    public LocalEventBus(WebSocketBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @Override
    public void publish(AuctionEvent event) {
        broadcaster.dispatch(event);
    }
}
