package com.sis.backend.controller;

import com.sis.backend.core.autocomplete.TrieAutocompleteService;
import com.sis.backend.model.Course;
import com.sis.backend.service.CourseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    /**
     * Get all courses (served from Redis Key-Value Cache, supports optional department filter).
     * GET /api/courses
     * GET /api/courses?department=CSE
     */
    @GetMapping("/courses")
    public ResponseEntity<Map<String, Object>> getAllCourses(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) Integer semester,
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) String status) {

        long start = System.currentTimeMillis();
        List<Course> courses = courseService.getFilteredCourses(department, semester, academicYear, status);
        long duration = System.currentTimeMillis() - start;

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("count", courses.size());
        response.put("responseTimeMs", duration);
        response.put("courses", courses);
        return ResponseEntity.ok(response);
    }

    /**
     * Instant Course Autocomplete Search via Prefix Trie.
     * GET /api/search/suggest?query=CS
     */
    @GetMapping("/search/suggest")
    public ResponseEntity<Map<String, Object>> suggestCourses(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit) {

        long start = System.nanoTime();
        List<TrieAutocompleteService.CourseSuggestion> suggestions = courseService.getSuggestions(query, limit);
        long durationMicros = (System.nanoTime() - start) / 1000;

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("query", query);
        response.put("searchTimeMicros", durationMicros);
        response.put("suggestions", suggestions);
        return ResponseEntity.ok(response);
    }
}
