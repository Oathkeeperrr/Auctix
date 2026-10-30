package com.auctix.config;

import com.auctix.realtime.RedisEventSubscriber;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Wires the Redis pub/sub listener that keeps every backend instance in sync.
 */
@Configuration
@ConditionalOnProperty(prefix = "auctix.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisConfig {

    @Bean
    public ChannelTopic auctionEventTopic(AuctixProperties properties) {
        return new ChannelTopic(properties.getRedis().getChannel());
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory,
                                                                        RedisEventSubscriber subscriber,
                                                                        ChannelTopic auctionEventTopic) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, auctionEventTopic);
        return container;
    }
}
