package com.sis.backend.core.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Distributed Rate Limiter backed by Redis (Token / Counter Window).
 * Protects Authentication endpoints from brute force and DoS attacks.
 */
@Service
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;

    // Live metrics counters for System Design Dashboard
    private final AtomicLong allowedRequests = new AtomicLong(0);
    private final AtomicLong rejectedRequests = new AtomicLong(0);

    // In-memory fallback if Redis is unavailable
    private final ConcurrentHashMap<String, InMemoryBucket> memoryBuckets = new ConcurrentHashMap<>();

    public RateLimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Check if request should be allowed.
     *
     * @param key           identifier (e.g. email or client IP)
     * @param maxRequests   maximum allowed hits within the window
     * @param windowSeconds time window in seconds
     * @return true if permitted, false if rate limited
     */
    public boolean tryAcquire(String key, int maxRequests, long windowSeconds) {
        String redisKey = "ratelimit:" + key;

        try {
            Long count = redisTemplate.opsForValue().increment(redisKey);
            if (count != null) {
                if (count == 1) {
                    redisTemplate.expire(redisKey, Duration.ofSeconds(windowSeconds));
                }
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
