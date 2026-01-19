package com.fdt.order.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fdt.order.dto.OrderResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Lightweight write-through cache for "active" orders.
 *
 * <p>Orders that aren't terminal are accessed often (driver app polling,
 * customer status checks, notification fan-out), so we keep a JSON snapshot
 * in Redis with a short TTL. The DB remains the source of truth.
 */
@Component
public class ActiveOrderCache {

    private static final Logger log = LoggerFactory.getLogger(ActiveOrderCache.class);
    private static final String KEY_PREFIX = "order:active:";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Duration ttl;

    public ActiveOrderCache(
            StringRedisTemplate redis,
            ObjectMapper mapper,
            @Value("${fdt.cache.active-order-ttl-seconds:300}") long ttlSeconds) {
        this.redis = redis;
        this.mapper = mapper;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public void put(OrderResponse order) {
        try {
            String json = mapper.writeValueAsString(order);
            redis.opsForValue().set(key(order.id()), json, ttl);
        } catch (Exception e) {
            // Cache failures must never break the request path.
            log.warn("Failed to cache order {}: {}", order.id(), e.toString());
        }
    }

    public Optional<OrderResponse> get(UUID id) {
        try {
            String json = redis.opsForValue().get(key(id));
            if (json == null) return Optional.empty();
            return Optional.of(mapper.readValue(json, OrderResponse.class));
        } catch (Exception e) {
            log.warn("Failed to read cache for order {}: {}", id, e.toString());
            return Optional.empty();
        }
    }

    public void invalidate(UUID id) {
        try {
            redis.delete(key(id));
        } catch (Exception e) {
            log.warn("Failed to invalidate cache for order {}: {}", id, e.toString());
        }
    }

    private String key(UUID id) {
        return KEY_PREFIX + id;
    }
}
