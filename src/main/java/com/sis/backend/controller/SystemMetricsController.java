package com.sis.backend.controller;

import com.sis.backend.core.autocomplete.TrieAutocompleteService;
import com.sis.backend.core.crawler.WebCrawlerService;
import com.sis.backend.core.hashing.ConsistentHashRing;
import com.sis.backend.core.kv.KeyValueCacheService;
import com.sis.backend.core.ratelimit.RateLimiterService;
import com.sis.backend.core.sharding.MongoShardRouter;
import com.sis.backend.core.url.UrlShortenerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * System Design Metrics Controller.
 * Provides real-time metrics across all 8 System Design Components
 * for demonstration, JMeter benchmarking, and dashboard visualization.
 */
@RestController
@RequestMapping("/api/system")
@CrossOrigin(origins = "*")
public class SystemMetricsController {

    private final MongoShardRouter mongoShardRouter;
    private final ConsistentHashRing consistentHashRing;
    private final KeyValueCacheService keyValueCacheService;
    private final RateLimiterService rateLimiterService;
    private final TrieAutocompleteService trieAutocompleteService;
    private final UrlShortenerService urlShortenerService;
    private final WebCrawlerService webCrawlerService;
    private final com.sis.backend.core.realtime.RealtimeEventService realtimeEventService;

    public SystemMetricsController(MongoShardRouter mongoShardRouter,
                                   ConsistentHashRing consistentHashRing,
                                   KeyValueCacheService keyValueCacheService,
                                   RateLimiterService rateLimiterService,
                                   TrieAutocompleteService trieAutocompleteService,
                                   UrlShortenerService urlShortenerService,
                                   WebCrawlerService webCrawlerService,
                                   com.sis.backend.core.realtime.RealtimeEventService realtimeEventService) {
        this.mongoShardRouter = mongoShardRouter;
        this.consistentHashRing = consistentHashRing;
        this.keyValueCacheService = keyValueCacheService;
        this.rateLimiterService = rateLimiterService;
        this.trieAutocompleteService = trieAutocompleteService;
        this.urlShortenerService = urlShortenerService;
        this.webCrawlerService = webCrawlerService;
        this.realtimeEventService = realtimeEventService;
    }

    /**
     * Unified System Metrics across all 8 System Design Components.
     * GET /api/system/metrics
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getSystemMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        // 1. Sharding & Consistent Hashing
        Map<String, Object> sharding = new LinkedHashMap<>();
        List<String> sortedShards = new ArrayList<>(consistentHashRing.getPhysicalShards());
        Collections.sort(sortedShards);
        sharding.put("activeShards", sortedShards);
        sharding.put("virtualNodesInRing", consistentHashRing.getRingSize());
        sharding.put("virtualNodesPerShard", consistentHashRing.getVirtualNodesPerShard());
        sharding.put("shardRecordDistribution", mongoShardRouter.getDistribution());
        sharding.put("virtualNodeDistribution", consistentHashRing.getVirtualNodeDistribution());
        metrics.put("shardingAndConsistentHashing", sharding);

        // 2. Key-Value Store (Redis Cache)
        Map<String, Object> cache = new HashMap<>();
        cache.put("hits", keyValueCacheService.getCacheHits());
        cache.put("misses", keyValueCacheService.getCacheMisses());
        cache.put("hitRatioPercent", Math.round(keyValueCacheService.getHitRatio() * 100.0) / 100.0);
        metrics.put("keyValueStoreRedis", cache);

        // 3. Rate Limiter (Token Bucket)
        Map<String, Object> rateLimiter = new HashMap<>();
        rateLimiter.put("allowedRequests", rateLimiterService.getAllowedCount());
        rateLimiter.put("rejectedRequests", rateLimiterService.getRejectedCount());
        metrics.put("rateLimiter", rateLimiter);

        // 4. Autocomplete
        Map<String, Object> autocomplete = new HashMap<>();
        autocomplete.put("totalIndexedTerms", trieAutocompleteService.getTotalIndexedTerms());
        metrics.put("trieAutocomplete", autocomplete);

        // 5. URL Shortener
        Map<String, Object> urlShortener = new HashMap<>();
        urlShortener.put("totalShortenedUrls", urlShortenerService.getTotalShortenedCount());
        metrics.put("urlShortener", urlShortener);

        // 6. Web Crawler
        Map<String, Object> crawler = new HashMap<>();
        crawler.put("crawlCyclesExecuted", webCrawlerService.getCrawlCyclesCount());
        metrics.put("webCrawler", crawler);

        metrics.put("status", "HEALTHY");
        metrics.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(metrics);
    }

    /**
     * Inspect Consistent Hash Ring state & resolve a key.
     * GET /api/system/hash-ring?key=370142058834432000
     */
    @GetMapping("/hash-ring")
    public ResponseEntity<Map<String, Object>> getHashRing(@RequestParam(required = false) String key) {
        Map<String, Object> response = new LinkedHashMap<>();
        List<String> sortedShards = new ArrayList<>(consistentHashRing.getPhysicalShards());
        Collections.sort(sortedShards);
        response.put("activeShards", sortedShards);
        response.put("totalVirtualNodes", consistentHashRing.getRingSize());
        response.put("virtualNodesPerShard", consistentHashRing.getVirtualNodesPerShard());
        response.put("virtualNodeDistribution", consistentHashRing.getVirtualNodeDistribution());

        if (key != null && !key.isBlank()) {
            response.put("testedKey", key);
            response.put("routedShard", consistentHashRing.getShard(key));
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Elastic Scaling Demo: Add a new shard dynamically to the hash ring.
     * POST /api/system/shards/add?shard=sis_shard_3
     */
    @PostMapping("/shards/add")
    public ResponseEntity<Map<String, Object>> addShard(@RequestParam String shard) {
        boolean alreadyExists = consistentHashRing.getPhysicalShards().contains(shard);
        Map<String, Object> result = mongoShardRouter.addShardAndRebalance(shard);

        if (!alreadyExists) {
            realtimeEventService.emitShardChange("ADDED", shard,
                    consistentHashRing.getPhysicalShards().size(),
                    consistentHashRing.getRingSize());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Trigger manual cluster-wide rebalancing across all physical shards.
     * POST /api/system/shards/rebalance
     */
    @PostMapping("/shards/rebalance")
    public ResponseEntity<Map<String, Object>> rebalanceCluster() {
        Map<String, Object> stats = mongoShardRouter.rebalanceCluster();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Cluster rebalanced successfully via consistent hashing.");
        response.put("migratedStudents", stats.get("migratedStudents"));
        response.put("migratedEnrollments", stats.get("migratedEnrollments"));
        response.put("distribution", mongoShardRouter.getDistribution());
        response.put("totalVirtualNodes", consistentHashRing.getRingSize());
        response.put("virtualNodeDistribution", consistentHashRing.getVirtualNodeDistribution());
        return ResponseEntity.ok(response);
    }

    /**
     * SAFE Shard Decommission: Drains all data to successor shards via consistent hashing,
     * then removes shard from the ring. No data loss.
     * POST /api/system/shards/drain-remove?shard=sis_shard_2
     */
    @PostMapping("/shards/drain-remove")
    public ResponseEntity<Map<String, Object>> drainAndRemoveShard(@RequestParam String shard) {
        Map<String, Object> result = mongoShardRouter.drainAndRemoveShard(shard);

        if (Boolean.TRUE.equals(result.get("success"))) {
            realtimeEventService.emitShardChange("DRAINED_AND_REMOVED", shard,
                    consistentHashRing.getPhysicalShards().size(),
                    consistentHashRing.getRingSize());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Legacy remove endpoint - still available but now returns guidance to use drain-remove.
     * POST /api/system/shards/remove?shard=sis_shard_2
     */
    @PostMapping("/shards/remove")
    public ResponseEntity<Map<String, Object>> removeShard(@RequestParam String shard) {
        // Delegate to safe drain-remove to prevent data loss
        return drainAndRemoveShard(shard);
    }
}
