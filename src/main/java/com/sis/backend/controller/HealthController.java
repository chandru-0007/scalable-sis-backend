package com.sis.backend.controller;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class HealthController {

    private final MongoTemplate mongoTemplate;
    private final StringRedisTemplate redisTemplate;

    public HealthController(MongoTemplate mongoTemplate, StringRedisTemplate redisTemplate) {
        this.mongoTemplate = mongoTemplate;
        this.redisTemplate = redisTemplate;
    }

    /**
     * GET /api/health
     * Returns status of the server, MongoDB, and Redis connections.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> status = new LinkedHashMap<>();

        status.put("server", "UP");

        // Check MongoDB
        try {
            mongoTemplate.getDb().runCommand(new org.bson.Document("ping", 1));
            String mongoUri = System.getProperty("MONGO_URI", "local");
            status.put("mongodb", "UP");
            status.put("mongodbProvider", mongoUri.contains("mongodb.net") ? "MongoDB Atlas Cloud Cluster" : "Local MongoDB");
        } catch (Exception e) {
            status.put("mongodb", "DOWN - " + e.getMessage());
        }

        // Check Redis (Upstash Cloud Redis or Local Memurai)
        try {
            String pong = redisTemplate.getConnectionFactory()
                    .getConnection().ping();
            String redisHost = System.getProperty("REDIS_HOST", "localhost");
            status.put("redis", "UP - " + pong);
            status.put("redisProvider", redisHost.contains("upstash") ? "Upstash Cloud Redis (" + redisHost + ")" : "Local Redis / Memurai (" + redisHost + ")");
        } catch (Exception e) {
            status.put("redis", "DOWN - " + e.getMessage());
        }

        return ResponseEntity.ok(status);
    }
}
