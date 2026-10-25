package com.sis.backend.service;

import com.sis.backend.model.Student;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Key-Value Session Store backed by Redis.
 * Caches student authentication tokens with TTL (24 hours).
 */
@Service
public class AuthSessionService {

    private static final String PREFIX = "session:";
    private static final Duration SESSION_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ConcurrentHashMap<String, Map<String, String>> memorySessions = new ConcurrentHashMap<>();

    public AuthSessionService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Create and store a new student session.
     */
    public String createSession(Student student) {
        String token = "sis_sess_" + UUID.randomUUID().toString().replace("-", "");

        Map<String, String> sessionData = new HashMap<>();
        sessionData.put("studentId", student.getId());
        sessionData.put("name", student.getName());
        sessionData.put("email", student.getEmail());
        sessionData.put("rollNumber", student.getRollNumber());
        sessionData.put("department", student.getDepartment());
        sessionData.put("semester", String.valueOf(student.getSemester()));
        sessionData.put("role", student.getRole() != null ? student.getRole() : "STUDENT");
        sessionData.put("targetShard", student.getTargetShard());

        try {
            // Save hash entries in Redis
            String key = PREFIX + token;
            redisTemplate.opsForHash().putAll(key, sessionData);
            redisTemplate.expire(key, SESSION_TTL);
        } catch (Exception e) {
            // Memory fallback
            memorySessions.put(token, sessionData);
        }

        return token;
    }

    /**
     * Retrieve cached session by token.
     */
    public Map<String, String> getSession(String token) {
        try {
            String key = PREFIX + token;
            Map<Object, Object> raw = redisTemplate.opsForHash().entries(key);
            if (raw != null && !raw.isEmpty()) {
                Map<String, String> result = new HashMap<>();
                raw.forEach((k, v) -> result.put(String.valueOf(k), String.valueOf(v)));
                return result;
            }
        } catch (Exception ignored) {
        }

        return memorySessions.get(token);
    }

    /**
     * Update active session data (e.g. when student profile or semester changes).
     */
    public void updateSession(String token, Map<String, String> updatedData) {
        if (token == null || updatedData == null) return;
        try {
            String key = PREFIX + token;
            redisTemplate.opsForHash().putAll(key, updatedData);
            redisTemplate.expire(key, SESSION_TTL);
        } catch (Exception e) {
            // Memory fallback
            memorySessions.put(token, updatedData);
        }
    }

    /**
     * Invalidate/Delete session (logout).
     */
    public boolean deleteSession(String token) {
        boolean removed = false;
        try {
            Boolean res = redisTemplate.delete(PREFIX + token);
            removed = Boolean.TRUE.equals(res);
        } catch (Exception ignored) {
        }
        if (memorySessions.remove(token) != null) {
            removed = true;
        }
        return removed;
    }
}
