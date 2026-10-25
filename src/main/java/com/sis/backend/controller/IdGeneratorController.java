package com.sis.backend.controller;

import com.sis.backend.core.id.SnowflakeIdGenerator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/id")
@CrossOrigin(origins = "*")
public class IdGeneratorController {

    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public IdGeneratorController(SnowflakeIdGenerator snowflakeIdGenerator) {
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    /**
     * Generate unique Snowflake ID(s).
     * Example: GET /api/id/generate?count=5
     */
    @GetMapping("/generate")
    public ResponseEntity<Map<String, Object>> generateIds(
            @RequestParam(defaultValue = "1") int count) {

        int safeCount = Math.min(Math.max(count, 1), 1000); // capped between 1 and 1000
        List<String> ids = new ArrayList<>(safeCount);

        long start = System.nanoTime();
        for (int i = 0; i < safeCount; i++) {
            ids.add(snowflakeIdGenerator.nextIdStr());
        }
        long durationMicros = (System.nanoTime() - start) / 1000;

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("algorithm", "Twitter Snowflake (64-bit)");
        response.put("count", safeCount);
        response.put("generationTimeMicros", durationMicros);
        response.put("ids", ids);

        return ResponseEntity.ok(response);
    }

    /**
     * Parse/Deconstruct an existing Snowflake ID into its 4 components.
     * Example: GET /api/id/parse?id=92928374829102464
     */
    @GetMapping("/parse")
    public ResponseEntity<Map<String, Object>> parseId(@RequestParam long id) {
        Map<String, Object> parsed = snowflakeIdGenerator.parseId(id);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("parsedComponents", parsed);
        return ResponseEntity.ok(response);
    }
}
