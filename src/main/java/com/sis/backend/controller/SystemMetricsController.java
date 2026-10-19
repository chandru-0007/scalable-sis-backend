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

import java.util.HashMap;
import java.util.Map;

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

    public SystemMetricsController(MongoShardRouter mongoShardRouter,
                                   ConsistentHashRing consistentHashRing,
                                   KeyValueCacheService keyValueCacheService,
                                   RateLimiterService rateLimiterService,
                                   TrieAutocompleteService trieAutocompleteService,
                                   UrlShortenerService urlShortenerService,
                                   WebCrawlerService webCrawlerService) {
        this.mongoShardRouter = mongoShardRouter;
        this.consistentHashRing = consistentHashRing;
        this.keyValueCacheService = keyValueCacheService;
        this.rateLimiterService = rateLimiterService;
        this.trieAutocompleteService = trieAutocompleteService;
        this.urlShortenerService = urlShortenerService;
        this.webCrawlerService = webCrawlerService;
    }

    /**
     * Unified System Metrics across all 8 System Design Components.
     * GET /api/system/metrics
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getSystemMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        // 1. Sharding & Consistent Hashing
        Map<String, Object> sharding = new HashMap<>();
        sharding.put("activeShards", consistentHashRing.getPhysicalShards());
        sharding.put("virtualNodesInRing", consistentHashRing.getRingSize());
        sharding.put("shardRecordDistribution", mongoShardRouter.getDistribution());
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
        Map<String, Object> response = new HashMap<>();
        response.put("activeShards", consistentHashRing.getPhysicalShards());
        response.put("totalVirtualNodes", consistentHashRing.getRingSize());

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
        consistentHashRing.addShard(shard);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Shard " + shard + " added dynamically to the hash ring.");
        response.put("updatedShards", consistentHashRing.getPhysicalShards());
        response.put("totalVirtualNodes", consistentHashRing.getRingSize());
        return ResponseEntity.ok(response);
    }

    /**
     * Failover Demo: Remove a shard dynamically from the hash ring.
     * POST /api/system/shards/remove?shard=sis_shard_2
     */
    @PostMapping("/shards/remove")
    public ResponseEntity<Map<String, Object>> removeShard(@RequestParam String shard) {
        boolean removed = consistentHashRing.removeShard(shard);
        Map<String, Object> response = new HashMap<>();
        response.put("status", removed ? "SUCCESS" : "NOT_FOUND");
        response.put("message", removed ? "Shard " + shard + " removed from the hash ring." : "Shard not found");
        response.put("remainingShards", consistentHashRing.getPhysicalShards());
        response.put("totalVirtualNodes", consistentHashRing.getRingSize());
        return ResponseEntity.ok(response);
    }
}
