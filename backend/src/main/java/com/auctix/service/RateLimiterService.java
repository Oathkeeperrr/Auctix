package com.auctix.service;

import com.auctix.config.AuctixProperties;
import com.auctix.exception.ApiException;
import com.auctix.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window rate limiter for bid submission.
 *
 * <p>Uses Redis so the limit holds across backend instances, and falls back to an in-process
 * counter when Redis is disabled or unreachable.</p>
 */
@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    private final AuctixProperties properties;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final Map<String, Window> localWindows = new ConcurrentHashMap<>();

    public RateLimiterService(AuctixProperties properties,
                              ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.properties = properties;
        this.redisTemplateProvider = redisTemplateProvider;
    }

    /**
     * @throws ApiException with {@link ErrorCode#RATE_LIMITED} when the caller is bidding too fast
     */
    public void checkBidAllowed(Long userId) {
        AuctixProperties.Bidding config = properties.getBidding();
        if (!config.isRateLimitEnabled()) {
            return;
        }
        String key = "auctix:ratelimit:bid:" + userId;
        long count = increment(key, config.getRateLimitWindowSeconds());
        if (count > config.getRateLimitMaxBids()) {
            throw new ApiException(ErrorCode.RATE_LIMITED,
                    "Maximum " + config.getRateLimitMaxBids() + " bids per "
                            + config.getRateLimitWindowSeconds() + " seconds. Please slow down.",
                    Map.of("retryAfterSeconds", config.getRateLimitWindowSeconds()));
        }
    }

    private long increment(String key, int windowSeconds) {
        if (properties.getRedis().isEnabled()) {
            StringRedisTemplate template = redisTemplateProvider.getIfAvailable();
            if (template != null) {
                try {
                    Long value = template.opsForValue().increment(key);
                    if (value != null && value == 1L) {
                        template.expire(key, Duration.ofSeconds(windowSeconds));
                    }
                    return value == null ? 1L : value;
                } catch (RuntimeException ex) {
                    log.debug("Redis rate limiter unavailable, using local counter: {}", ex.getMessage());
                }
            }
        }
        return incrementLocal(key, windowSeconds);
    }

    private long incrementLocal(String key, int windowSeconds) {
        Instant now = Instant.now();
        Window window = localWindows.compute(key, (k, existing) -> {
            if (existing == null || existing.expiresAt.isBefore(now)) {
                return new Window(now.plusSeconds(windowSeconds));
            }
            return existing;
        });
        return window.counter.incrementAndGet();
    }

    /** Visible for tests. */
    public void reset() {
        localWindows.clear();
    }

    private static final class Window {
        private final Instant expiresAt;
        private final AtomicInteger counter = new AtomicInteger(0);

        private Window(Instant expiresAt) {
            this.expiresAt = expiresAt;
        }
    }
}
