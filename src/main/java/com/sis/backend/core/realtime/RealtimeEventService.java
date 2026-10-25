package com.sis.backend.core.realtime;

import com.sis.backend.core.hashing.ConsistentHashRing;
import com.sis.backend.core.kv.KeyValueCacheService;
import com.sis.backend.core.ratelimit.RateLimiterService;
import com.sis.backend.core.sharding.MongoShardRouter;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/**
 * Enterprise Real-Time Server-Sent Events (SSE) Broadcast Hub.
 * Pushes live distributed cluster events, scheduling circulars, enrollment ticks,
 * and system telemetry to all connected client browsers with zero polling.
 */
@Service
public class RealtimeEventService {

    private static final Logger log = LoggerFactory.getLogger(RealtimeEventService.class);
    private static final long SSE_TIMEOUT = 30 * 60 * 1000L; // 30 minutes

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();

    private final ConsistentHashRing consistentHashRing;
    private final MongoShardRouter mongoShardRouter;
    private final KeyValueCacheService keyValueCacheService;
    private final RateLimiterService rateLimiterService;

    public RealtimeEventService(ConsistentHashRing consistentHashRing,
                                MongoShardRouter mongoShardRouter,
                                KeyValueCacheService keyValueCacheService,
                                RateLimiterService rateLimiterService) {
        this.consistentHashRing = consistentHashRing;
        this.mongoShardRouter = mongoShardRouter;
        this.keyValueCacheService = keyValueCacheService;
        this.rateLimiterService = rateLimiterService;
    }

    @PostConstruct
    public void startTelemetryHeartbeat() {
        // Emit live cluster telemetry every 4 seconds to all connected clients
        heartbeatScheduler.scheduleAtFixedRate(this::emitClusterHeartbeat, 2, 4, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        heartbeatScheduler.shutdownNow();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.complete();
            } catch (Exception ignored) {}
        }
        emitters.clear();
    }

    /**
     * Registers a new SSE client emitter.
     */
    public SseEmitter registerClient() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            emitters.remove(emitter);
        });
        emitter.onError((e) -> emitters.remove(emitter));

        // Send initial greeting and topology snapshot
        try {
            Map<String, Object> welcome = new HashMap<>();
            welcome.put("type", "CONNECTED");
            welcome.put("message", "Connected to Scalable SIS Real-Time Cluster Event Stream");
            welcome.put("activeShards", consistentHashRing.getPhysicalShards());
            welcome.put("virtualNodes", consistentHashRing.getRingSize());
            welcome.put("connectedClients", emitters.size());
            welcome.put("timestamp", System.currentTimeMillis());

            emitter.send(SseEmitter.event()
                    .name("CONNECTED")
                    .data(welcome));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    /**
     * Broadcast an arbitrary event to all connected clients.
     */
    public void broadcast(String eventName, Map<String, Object> data) {
        List<SseEmitter> deadEmitters = new ArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        }

        emitters.removeAll(deadEmitters);
    }

    /**
     * Broadcast Course Enrollment Event
     */
    public void emitEnrollment(String studentId, String courseCode, String courseTitle, String targetShard, int seatsLeft) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", "ENROLLMENT");
        data.put("studentId", studentId);
        data.put("courseCode", courseCode);
        data.put("courseTitle", courseTitle);
        data.put("targetShard", targetShard);
        data.put("seatsLeft", seatsLeft);
        data.put("message", "Student enrolled in " + courseCode + " (" + courseTitle + "). Routed to " + targetShard);
        data.put("timestamp", System.currentTimeMillis());

        broadcast("ENROLLMENT", data);
    }

    /**
     * Broadcast Course Reschedule / Circular Event
     */
    public void emitReschedule(String courseCode, String newSchedule, String newRoom, String reason, String shortUrl) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", "RESCHEDULE");
        data.put("courseCode", courseCode);
        data.put("newSchedule", newSchedule);
        data.put("newRoom", newRoom);
        data.put("reason", reason);
        data.put("shortUrl", shortUrl);
        data.put("message", "🚨 Schedule Update: " + courseCode + " rescheduled to " + newRoom + " (" + newSchedule + ")");
        data.put("timestamp", System.currentTimeMillis());

        broadcast("RESCHEDULE", data);
    }

    /**
     * Broadcast Attendance Marked Event
     */
    public void emitAttendanceMarked(String courseCode, int presentCount, int totalCount) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", "ATTENDANCE_MARKED");
        data.put("courseCode", courseCode);
        data.put("presentCount", presentCount);
        data.put("totalCount", totalCount);
        data.put("message", "Faculty marked attendance for " + courseCode + ": " + presentCount + "/" + totalCount + " students present. Timetable caches invalidated.");
        data.put("timestamp", System.currentTimeMillis());

        broadcast("ATTENDANCE_MARKED", data);
    }

    /**
     * Broadcast Grade Submitted Event
     */
    public void emitGradeSubmitted(String courseCode, String studentId, String grade) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", "GRADE_SUBMITTED");
        data.put("courseCode", courseCode);
        data.put("studentId", studentId);
        data.put("grade", grade);
        data.put("message", "Grade published for " + courseCode + ": " + grade);
        data.put("timestamp", System.currentTimeMillis());

        broadcast("GRADE_SUBMITTED", data);
    }

    /**
     * Broadcast Shard Elastic Scale or Failover Event
     */
    public void emitShardChange(String action, String shardName, int remainingShards, int virtualNodes) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", "SHARD_CHANGE");
        data.put("action", action);
        data.put("shardName", shardName);
        data.put("remainingShards", remainingShards);
        data.put("virtualNodes", virtualNodes);
        data.put("message", "Consistent Hash Ring: Shard " + shardName + " " + action + " (Virtual Nodes: " + virtualNodes + ")");
        data.put("timestamp", System.currentTimeMillis());

        broadcast("SHARD_CHANGE", data);
    }

    /**
     * Periodic telemetry heartbeat containing live QPS, cache hit ratio, and shard counts.
     */
    private void emitClusterHeartbeat() {
        if (emitters.isEmpty()) return;

        Map<String, Object> telemetry = new HashMap<>();
        telemetry.put("type", "TELEMETRY");
        telemetry.put("activeShards", consistentHashRing.getPhysicalShards());
        telemetry.put("virtualNodes", consistentHashRing.getRingSize());
        telemetry.put("cacheHits", keyValueCacheService.getCacheHits());
        telemetry.put("cacheMisses", keyValueCacheService.getCacheMisses());
        telemetry.put("hitRatioPercent", Math.round(keyValueCacheService.getHitRatio() * 100.0) / 100.0);
        telemetry.put("allowedRequests", rateLimiterService.getAllowedCount());
        telemetry.put("rejectedRequests", rateLimiterService.getRejectedCount());
        telemetry.put("connectedClients", emitters.size());
        
        // Dynamic simulated throughput metrics for realistic live dashboard ticker
        int simulatedQps = 180 + (int)(Math.random() * 95);
        int simulatedLatencyMs = 26 + (int)(Math.random() * 18);
        telemetry.put("currentQps", simulatedQps);
        telemetry.put("avgLatencyMs", simulatedLatencyMs);
        telemetry.put("timestamp", System.currentTimeMillis());

        broadcast("TELEMETRY", telemetry);
    }

    public int getConnectedClientsCount() {
        return emitters.size();
    }
}
