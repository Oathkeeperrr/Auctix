package com.auctix.service;

import com.auctix.realtime.AuctionEventBus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Relays domain events onto the Redis/WebSocket bus once the surrounding transaction commits.
 * {@code fallbackExecution = true} keeps it working for callers that are not transactional.
 */
@Component
public class AuctionEventRelay {

    private final AuctionEventBus eventBus;

    public AuctionEventRelay(AuctionEventBus eventBus) {
        this.eventBus = eventBus;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAuctionDomainEvent(AuctionLifecycleService.AuctionDomainEvent domainEvent) {
        eventBus.publish(domainEvent.event());
    }
}
