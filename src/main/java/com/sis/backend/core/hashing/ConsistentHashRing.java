package com.sis.backend.core.hashing;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Consistent Hash Ring implementation with Virtual Nodes.
 *
 * Prevents hot-spotting and redistributes only (1/N) keys when a shard is added or removed.
 */
@Component
public class ConsistentHashRing {

    private static final int DEFAULT_VIRTUAL_NODES = 100;

    // Thread-safe sorted map representing the 360-degree hash ring
    private final SortedMap<Long, String> ring = new ConcurrentSkipListMap<>();
    private final Set<String> physicalShards = Collections.synchronizedSet(new HashSet<>());
    private final int numberOfReplicas;

    public ConsistentHashRing() {
        this(DEFAULT_VIRTUAL_NODES, List.of("sis_shard_0", "sis_shard_1", "sis_shard_2"));
    }

    public ConsistentHashRing(int numberOfReplicas, Collection<String> initialShards) {
        this.numberOfReplicas = numberOfReplicas;
        for (String shard : initialShards) {
            addShard(shard);
        }
    }

    /**
     * Add a physical shard and its virtual nodes onto the hash ring.
     */
    public synchronized void addShard(String shard) {
        physicalShards.add(shard);
        for (int i = 0; i < numberOfReplicas; i++) {
            long hash = hash(shard + "#VN_" + i);
            ring.put(hash, shard);
        }
    }

    /**
     * Remove a physical shard and its virtual nodes from the hash ring.
     */
    public synchronized boolean removeShard(String shard) {
        if (!physicalShards.contains(shard)) {
            return false;
        }
        physicalShards.remove(shard);
        for (int i = 0; i < numberOfReplicas; i++) {
            long hash = hash(shard + "#VN_" + i);
            ring.remove(hash);
        }
        return true;
    }

    /**
     * Locate the shard responsible for the given key (e.g., studentId).
     */
    public String getShard(String key) {
        if (ring.isEmpty()) {
            return "sis_shard_0";
        }
        long hash = hash(key);
        if (!ring.containsKey(hash)) {
            // Find the next node clockwise along the ring
            SortedMap<Long, String> tailMap = ring.tailMap(hash);
            hash = tailMap.isEmpty() ? ring.firstKey() : tailMap.firstKey();
        }
        return ring.get(hash);
    }

    /**
     * 64-bit MD5 hash truncation for smooth distribution around 2^64 ring.
     */
    private long hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
            long h = 0;
            for (int i = 0; i < 8; i++) {
                h = (h << 8) | (digest[i] & 0xFF);
            }
            return h;
        } catch (NoSuchAlgorithmException e) {
            // Fallback to Java hashCode if MD5 is unavailable
            return (long) key.hashCode();
        }
    }

    public Set<String> getPhysicalShards() {
        return new HashSet<>(physicalShards);
    }

    public int getRingSize() {
        return ring.size();
    }
}
