package com.auctix.realtime;

/**
 * Transport for live auction events.
 *
 * <p>Two implementations exist: {@link RedisEventBus} fans events out through Redis pub/sub so
 * any number of backend instances stay in sync, and {@link LocalEventBus} dispatches in-process
 * for single-instance runs and tests. Services always depend on this interface, never on Redis
 * directly.</p>
 */
public interface AuctionEventBus {

    void publish(AuctionEvent event);
}
