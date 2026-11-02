package com.auctix.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Receives events from Redis pub/sub and hands them to the local WebSocket sessions.
 */
@Component
@ConditionalOnProperty(prefix = "auctix.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisEventSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisEventSubscriber.class);

    private final ObjectMapper objectMapper;
    private final WebSocketBroadcaster broadcaster;

    public RedisEventSubscriber(ObjectMapper objectMapper, WebSocketBroadcaster broadcaster) {
        this.objectMapper = objectMapper;
        this.broadcaster = broadcaster;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            AuctionEvent event = objectMapper.readValue(body, AuctionEvent.class);
            broadcaster.dispatch(event);
        } catch (Exception ex) {
            log.error("Could not handle auction event from Redis: {}", ex.getMessage());
        }
    }
}
