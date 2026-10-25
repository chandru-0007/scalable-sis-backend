package com.sis.backend.core.url;

import com.sis.backend.core.id.SnowflakeIdGenerator;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Distributed URL Shortener combining 64-bit Snowflake IDs,
 * Base62 encoding, and Redis Key-Value store.
 */
@Service
public class UrlShortenerService {

    private static final String BASE62_ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String SHORT_TO_LONG_PREFIX = "url:code:";
    private static final String LONG_TO_SHORT_PREFIX = "url:target:";

    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final StringRedisTemplate redisTemplate;

    private final AtomicLong totalShortened = new AtomicLong(0);
    private final ConcurrentHashMap<String, String> memoryUrlMap = new ConcurrentHashMap<>();

    public UrlShortenerService(SnowflakeIdGenerator snowflakeIdGenerator,
                               StringRedisTemplate redisTemplate) {
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Shorten a long URL using 64-bit Snowflake ID and Base62 encoding.
     */
    public String shorten(String longUrl) {
        if (longUrl == null || longUrl.isBlank()) {
            throw new IllegalArgumentException("URL cannot be empty");
        }

        String trimmed = longUrl.trim();

        // 1. Check if already shortened in Redis
        try {
            String existingCode = redisTemplate.opsForValue().get(LONG_TO_SHORT_PREFIX + trimmed);
            if (existingCode != null) {
                return existingCode;
            }
        } catch (Exception ignored) {
        }

        // 2. Generate unique 64-bit ID
        long numericId = snowflakeIdGenerator.nextId();

        // 3. Encode to Base62 string
        String code = encodeBase62(numericId);

        // 4. Save bidirectional mapping in Key-Value store
        try {
            redisTemplate.opsForValue().set(SHORT_TO_LONG_PREFIX + code, trimmed);
            redisTemplate.opsForValue().set(LONG_TO_SHORT_PREFIX + trimmed, code);
        } catch (Exception e) {
            memoryUrlMap.put(code, trimmed);
            memoryUrlMap.put(trimmed, code);
        }

        totalShortened.incrementAndGet();
        return code;
    }

    /**
     * Resolve short code back to long URL.
     */
    public String resolve(String code) {
        try {
            String longUrl = redisTemplate.opsForValue().get(SHORT_TO_LONG_PREFIX + code);
            if (longUrl != null) return longUrl;
        } catch (Exception ignored) {
        }

        return memoryUrlMap.get(code);
    }

    private String encodeBase62(long value) {
        long num = Math.abs(value);
        StringBuilder sb = new StringBuilder();
        while (num > 0) {
            int remainder = (int) (num % 62);
            sb.append(BASE62_ALPHABET.charAt(remainder));
            num /= 62;
        }
        return sb.reverse().toString();
    }

    public long getTotalShortenedCount() {
        return totalShortened.get();
    }
}
