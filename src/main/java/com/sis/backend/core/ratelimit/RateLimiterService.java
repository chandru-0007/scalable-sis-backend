package com.sis.backend.core.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Distributed Rate Limiter backed by Redis and Atomic Lua Scripting.
 * Executes INCR and EXPIRE atomically in a single Redis engine thread execution.
 * Protects Authentication and Enrollment endpoints from brute force and bot attacks.
 */
@Service
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;

    // Live metrics counters for System Design Dashboard
    private final AtomicLong allowedRequests = new AtomicLong(0);
    private final AtomicLong rejectedRequests = new AtomicLong(0);

    // In-memory fallback if Redis is unavailable
    private final ConcurrentHashMap<String, InMemoryBucket> memoryBuckets = new ConcurrentHashMap<>();

    // Atomic Lua script: Increments key and sets TTL on first hit atomically
    private static final String LUA_RATE_LIMIT_SCRIPT =
            "local current = redis.call('INCR', KEYS[1])\n" +
            "if tonumber(current) == 1 then\n" +
            "    redis.call('EXPIRE', KEYS[1], ARGV[1])\n" +
            "end\n" +
            "return current";

    private final RedisScript<Long> rateLimitScript = new DefaultRedisScript<>(LUA_RATE_LIMIT_SCRIPT, Long.class);

    public RateLimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Check if request should be allowed using atomic Redis Lua Script.
     *
     * @param key           identifier (e.g. user rollNumber, studentId, or client IP)
     * @param maxRequests   maximum allowed hits within the window
     * @param windowSeconds time window in seconds
     * @return true if permitted, false if rate limited
     */
    public boolean tryAcquire(String key, int maxRequests, long windowSeconds) {
        String redisKey = "ratelimit:" + key;

        try {
            // Execute atomic Lua script on Redis: 1 network round trip, zero race condition
            Long count = redisTemplate.execute(
                    rateLimitScript,
                    Collections.singletonList(redisKey),
                    String.valueOf(windowSeconds)
            );

            if (count != null) {
                if (count > maxRequests) {
                    rejectedRequests.incrementAndGet();
                    return false;
                }
                allowedRequests.incrementAndGet();
                return true;
            }
        } catch (Exception e) {
            // Redis error fallback: handle in-memory
            return tryAcquireInMemory(key, maxRequests, windowSeconds);
        }

        return tryAcquireInMemory(key, maxRequests, windowSeconds);
    }

    private boolean tryAcquireInMemory(String key, int maxRequests, long windowSeconds) {
        long now = System.currentTimeMillis();
        long windowMillis = windowSeconds * 1000;

        InMemoryBucket bucket = memoryBuckets.compute(key, (k, existing) -> {
            if (existing == null || now - existing.windowStart > windowMillis) {
                return new InMemoryBucket(now, 1);
            } else {
                existing.count++;
                return existing;
            }
        });

        if (bucket.count > maxRequests) {
            rejectedRequests.incrementAndGet();
            return false;
        }

        allowedRequests.incrementAndGet();
        return true;
    }

    public long getAllowedCount() {
        return allowedRequests.get();
    }

    public long getRejectedCount() {
        return rejectedRequests.get();
    }

    private static class InMemoryBucket {
        long windowStart;
        int count;

        InMemoryBucket(long windowStart, int count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
