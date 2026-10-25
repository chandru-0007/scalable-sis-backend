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
 * Supports safe drain-remove (migrate data before decommissioning a shard).
 */
@Component
public class ConsistentHashRing {

    private static final int DEFAULT_VIRTUAL_NODES = 100;

    // Thread-safe sorted map representing the 360-degree hash ring
    private final SortedMap<Long, String> ring = new ConcurrentSkipListMap<>();
    private final Set<String> physicalShards = Collections.synchronizedSet(new LinkedHashSet<>());
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
        if (physicalShards.contains(shard)) return; // idempotent
        physicalShards.add(shard);
        for (int i = 0; i < numberOfReplicas; i++) {
            long hash = hash(shard + "#VN_" + i);
            ring.put(hash, shard);
        }
    }

    /**
     * Remove a physical shard and its virtual nodes from the hash ring.
     * IMPORTANT: Data migration must happen BEFORE calling this method.
     * Use MongoShardRouter.drainShard() first.
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
     * Uses clockwise lookup in the ConcurrentSkipListMap (O(log N)).
     */
    public String getShard(String key) {
        if (ring.isEmpty()) {
            return "sis_shard_0";
        }
        long hash = hash(key);
        SortedMap<Long, String> tailMap = ring.tailMap(hash);
        long resolvedKey = tailMap.isEmpty() ? ring.firstKey() : tailMap.firstKey();
        return ring.get(resolvedKey);
    }

    /**
     * Get the shard that will own keys currently mapped to the given shard after it is removed.
     * This is the successor shard in the ring (next clockwise physical node).
     */
    public String getSuccessorShard(String shardToRemove) {
        List<String> sorted = new ArrayList<>(physicalShards);
        sorted.sort(String::compareTo);
        int idx = sorted.indexOf(shardToRemove);
        if (idx < 0 || sorted.size() <= 1) return null;
        return sorted.get((idx + 1) % sorted.size());
    }

    /**
     * Get distribution of virtual nodes per physical shard.
     * Returns a map of shardName -> virtual node count.
     */
    public Map<String, Integer> getVirtualNodeDistribution() {
        Map<String, Integer> dist = new LinkedHashMap<>();
        for (String shard : physicalShards) {
            dist.put(shard, 0);
        }
        for (String shard : ring.values()) {
            dist.merge(shard, 1, Integer::sum);
        }
        return dist;
    }

    /**
     * Returns ordered list of (hashPosition, shardName) for ring visualization.
     * Normalizes hash positions to [0, 360] degrees.
     */
    public List<Map<String, Object>> getRingSegments() {
        if (ring.isEmpty()) return Collections.emptyList();

        long minHash = ring.firstKey();
        long maxHash = ring.lastKey();
        long range = maxHash - minHash;
        if (range == 0) range = 1;

        List<Map<String, Object>> segments = new ArrayList<>();
        for (Map.Entry<Long, String> entry : ring.entrySet()) {
            Map<String, Object> seg = new LinkedHashMap<>();
            double degree = ((double)(entry.getKey() - minHash) / (double) Long.MAX_VALUE) * 360.0;
            // Use absolute modulo trick for negative longs
            long absKey = entry.getKey();
            double normalizedDegree = (((double) absKey / (double) Long.MAX_VALUE) + 1.0) * 180.0;
            seg.put("degree", Math.round(normalizedDegree * 100.0) / 100.0);
            seg.put("shard", entry.getValue());
            segments.add(seg);
        }
        return segments;
    }

    /**
     * 64-bit MD5 hash truncation for smooth distribution around 2^64 ring.
     */
    public long hash(String key) {
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
        // Return in insertion order (LinkedHashSet)
        return new LinkedHashSet<>(physicalShards);
    }

    public int getRingSize() {
        return ring.size();
    }

    public int getVirtualNodesPerShard() {
        return numberOfReplicas;
    }
}
