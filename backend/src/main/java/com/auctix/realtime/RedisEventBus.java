package com.auctix.realtime;

import com.auctix.config.AuctixProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes every auction event onto a Redis channel. Each backend instance subscribes to the
 * same channel ({@link RedisEventSubscriber}), so a bid accepted on instance A reaches the
 * WebSocket sessions held by instance B. This is what makes horizontal scaling possible.
 */
@Component
@ConditionalOnProperty(prefix = "auctix.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisEventBus implements AuctionEventBus {

    private static final Logger log = LoggerFactory.getLogger(RedisEventBus.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AuctixProperties properties;
    private final WebSocketBroadcaster fallbackBroadcaster;

    public RedisEventBus(StringRedisTemplate redisTemplate,
                         ObjectMapper objectMapper,
                         AuctixProperties properties,
                         WebSocketBroadcaster fallbackBroadcaster) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.fallbackBroadcaster = fallbackBroadcaster;
    }

    @Override
    public void publish(AuctionEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            redisTemplate.convertAndSend(properties.getRedis().getChannel(), payload);
        } catch (JsonProcessingException ex) {
            log.error("Could not serialise auction event {}", event.type(), ex);
        } catch (RuntimeException ex) {
            // Redis unreachable: keep the platform usable by delivering locally.
            log.warn("Redis publish failed ({}), falling back to local dispatch", ex.getMessage());
            fallbackBroadcaster.dispatch(event);
        }
    }
}
