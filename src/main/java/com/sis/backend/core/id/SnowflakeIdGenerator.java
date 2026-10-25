package com.sis.backend.core.id;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Twitter Snowflake 64-bit Distributed Unique ID Generator
 *
 * Structure:
 * 1 bit   : Sign bit (always 0)
 * 41 bits : Milliseconds since custom epoch (Jan 1, 2024 00:00:00 UTC)
 * 5 bits  : Datacenter ID (0 - 31)
 * 5 bits  : Worker / Machine ID (0 - 31)
 * 12 bits : Sequence number (0 - 4095 per millisecond)
 *
 * Capabilities:
 * - Up to 4,096 unique IDs per millisecond per worker node
 * - Globally unique across distributed nodes without coordination
 * - Naturally time-ordered (k-sorted)
 */
@Component
public class SnowflakeIdGenerator {

    // Custom epoch: 2024-01-01 00:00:00 UTC (in milliseconds)
    private static final long CUSTOM_EPOCH = 1704067200000L;

    // Bit allocations
    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    // Maximum values
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);        // 31
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS); // 31
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);          // 4095

    // Bit shifts
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    private final long datacenterId;
    private final long workerId;

    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator(
            @Value("${sis.snowflake.datacenter-id:1}") long datacenterId,
            @Value("${sis.snowflake.worker-id:1}") long workerId) {

        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException(
                    String.format("Datacenter ID must be between 0 and %d", MAX_DATACENTER_ID));
        }
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException(
                    String.format("Worker ID must be between 0 and %d", MAX_WORKER_ID));
        }

        this.datacenterId = datacenterId;
        this.workerId = workerId;
    }

    /**
     * Generate the next globally unique 64-bit ID (thread-safe).
     */
    public synchronized long nextId() {
        long currentTimestamp = getCurrentTimeMillis();

        if (currentTimestamp < lastTimestamp) {
            long offset = lastTimestamp - currentTimestamp;
            if (offset <= 5) {
                // Wait briefly if clock drift is minimal
                try {
                    Thread.sleep(offset << 1);
                    currentTimestamp = getCurrentTimeMillis();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for clock", e);
                }
            } else {
                throw new IllegalStateException(
                        String.format("Clock moved backwards. Refusing to generate ID for %d ms", offset));
            }
        }

        if (currentTimestamp == lastTimestamp) {
            // Same millisecond: increment sequence counter
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // Sequence overflow: wait until next millisecond
                currentTimestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            // New millisecond: reset sequence counter
            sequence = 0L;
        }

        lastTimestamp = currentTimestamp;

        return ((currentTimestamp - CUSTOM_EPOCH) << TIMESTAMP_LEFT_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    /**
     * Convenience method returning ID as String to prevent JavaScript 53-bit precision loss in frontend.
     */
    public String nextIdStr() {
        return String.valueOf(nextId());
    }

    /**
     * Deconstruct a Snowflake ID back into its component parts (for inspection/debugging).
     */
    public Map<String, Object> parseId(long id) {
        long timestampDelta = (id >> TIMESTAMP_LEFT_SHIFT);
        long originalTimestamp = timestampDelta + CUSTOM_EPOCH;
        long extractedDatacenterId = (id >> DATACENTER_ID_SHIFT) & MAX_DATACENTER_ID;
        long extractedWorkerId = (id >> WORKER_ID_SHIFT) & MAX_WORKER_ID;
        long extractedSequence = id & MAX_SEQUENCE;

        String formattedDate = DateTimeFormatter.ISO_INSTANT
                .format(Instant.ofEpochMilli(originalTimestamp).atZone(ZoneId.of("UTC")));

        Map<String, Object> details = new HashMap<>();
        details.put("id", String.valueOf(id));
        details.put("timestampMs", originalTimestamp);
        details.put("formattedUtcTime", formattedDate);
        details.put("datacenterId", extractedDatacenterId);
        details.put("workerId", extractedWorkerId);
        details.put("sequence", extractedSequence);

        return details;
    }

    private long waitNextMillis(long lastTimestamp) {
        long timestamp = getCurrentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = getCurrentTimeMillis();
        }
        return timestamp;
    }

    private long getCurrentTimeMillis() {
        return System.currentTimeMillis();
    }

    public long getDatacenterId() {
        return datacenterId;
    }

    public long getWorkerId() {
        return workerId;
    }
}
