package com.sis.backend.controller;

import com.sis.backend.core.realtime.RealtimeEventService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for Real-Time SSE Stream and Live Traffic Simulation.
 */
@RestController
@RequestMapping("/api/realtime")
@CrossOrigin(origins = "*")
public class RealtimeEventController {

    private final RealtimeEventService realtimeEventService;

    public RealtimeEventController(RealtimeEventService realtimeEventService) {
        this.realtimeEventService = realtimeEventService;
    }

    /**
     * Client endpoint to establish Server-Sent Events stream.
     * GET /api/realtime/stream
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToStream() {
        return realtimeEventService.registerClient();
    }

    /**
     * Active clients status.
     * GET /api/realtime/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("connectedClients", realtimeEventService.getConnectedClientsCount());
        status.put("status", "ACTIVE");
        status.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(status);
    }

    /**
     * Simulate a load spike / distributed scatter-gather traffic burst.
     * POST /api/realtime/simulate-traffic
     */
    @PostMapping("/simulate-traffic")
    public ResponseEntity<Map<String, Object>> simulateTraffic(@RequestParam(defaultValue = "15") int requests) {
        int simulatedQps = 320 + (int)(Math.random() * 180);
        int scatterGatherLatency = 24 + (int)(Math.random() * 15);

        Map<String, Object> burstData = new HashMap<>();
        burstData.put("type", "TRAFFIC_BURST");
        burstData.put("simulatedRequests", requests);
        burstData.put("qpsPeak", simulatedQps);
        burstData.put("scatterGatherLatencyMs", scatterGatherLatency);
        burstData.put("message", "Simulated burst: " + requests + " concurrent scatter-gather queries processed across MongoDB Atlas shards with " + scatterGatherLatency + "ms p99 latency.");
        burstData.put("timestamp", System.currentTimeMillis());

        realtimeEventService.broadcast("TRAFFIC_BURST", burstData);

        return ResponseEntity.ok(burstData);
    }
}
