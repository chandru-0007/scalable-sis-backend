package com.sis.backend.core.kv;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Key-Value Store Service backed by Redis.
 * Caches frequently queried data (Courses, Timetables, Notices)
 * and maintains hit/miss metrics for the System Design Dashboard.
 */
@Service
public class KeyValueCacheService {

    private final StringRedisTemplate redisTemplate;

    // Live metrics for Dashboard & Deliverables
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);

    // In-memory fallback if Redis is momentarily unavailable
    private final ConcurrentHashMap<String, String> memoryCache = new ConcurrentHashMap<>();

    public KeyValueCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void put(String key, String value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            memoryCache.put(key, value);
        }
    }

    public String get(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                cacheHits.incrementAndGet();
                return value;
            }
        } catch (Exception ignored) {
        }

        String fallbackVal = memoryCache.get(key);
        if (fallbackVal != null) {
            cacheHits.incrementAndGet();
            return fallbackVal;
        }

        cacheMisses.incrementAndGet();
        return null;
    }

    public void evict(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception ignored) {
        }
        memoryCache.remove(key);
    }

    public long getCacheHits() {
        return cacheHits.get();
    }

    public long getCacheMisses() {
        return cacheMisses.get();
    }

    public double getHitRatio() {
        long hits = cacheHits.get();
        long total = hits + cacheMisses.get();
        if (total == 0) return 0.0;
        return ((double) hits / total) * 100.0;
    }
}
