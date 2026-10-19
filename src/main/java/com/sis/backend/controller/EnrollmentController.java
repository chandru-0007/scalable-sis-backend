package com.sis.backend.controller;

import com.sis.backend.model.Enrollment;
import com.sis.backend.model.TimetableEntry;
import com.sis.backend.service.EnrollmentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    public EnrollmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    /**
     * Course Enrollment with Rate Limiting and Shard Routing.
     * POST /api/courses/enroll
     */
    @PostMapping("/courses/enroll")
    public ResponseEntity<Map<String, Object>> enroll(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        String studentId = request.get("studentId");
        String courseCode = request.get("courseCode");

        if (studentId == null || courseCode == null) {
            response.put("status", "ERROR");
            response.put("message", "studentId and courseCode are required.");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            Enrollment enrollment = enrollmentService.enroll(studentId, courseCode);
            response.put("status", "SUCCESS");
            response.put("message", "Enrolled successfully in " + courseCode);
            response.put("enrollment", enrollment);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalStateException e) {
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
        } catch (IllegalArgumentException e) {
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Access Student Timetable (served from Redis Key-Value Store).
     * GET /api/students/{id}/timetable
     */
    @GetMapping("/students/{id}/timetable")
    public ResponseEntity<Map<String, Object>> getTimetable(@PathVariable("id") String studentId) {
        long start = System.currentTimeMillis();
        List<TimetableEntry> timetable = enrollmentService.getStudentTimetable(studentId);
        long duration = System.currentTimeMillis() - start;

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("studentId", studentId);
        response.put("slotsCount", timetable.size());
        response.put("responseTimeMs", duration);
        response.put("timetable", timetable);
        return ResponseEntity.ok(response);
    }

    /**
     * Get All Enrolled Courses for a Student (Grades + Attendance).
     * GET /api/students/{id}/enrollments
     */
    @GetMapping("/students/{id}/enrollments")
    public ResponseEntity<Map<String, Object>> getEnrollments(@PathVariable("id") String studentId) {
        List<Enrollment> enrollments = enrollmentService.getStudentEnrollments(studentId);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("studentId", studentId);
        response.put("enrollments", enrollments);
        return ResponseEntity.ok(response);
    }

    /**
     * Examination & Grade Management.
     * PUT /api/enrollments/{id}/grade
     */
    @PutMapping("/enrollments/{id}/grade")
    public ResponseEntity<Map<String, Object>> updateGrade(
            @PathVariable("id") String enrollmentId,
            @RequestBody Map<String, Object> body) {

        String grade = String.valueOf(body.get("grade"));
        double gradePoint = Double.parseDouble(String.valueOf(body.getOrDefault("gradePoint", 0.0)));

        Enrollment updated = enrollmentService.updateGrade(enrollmentId, grade, gradePoint);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Grade updated successfully");
        response.put("enrollment", updated);
        return ResponseEntity.ok(response);
    }

    /**
     * Attendance Tracking Management.
     * PUT /api/enrollments/{id}/attendance
     */
    @PutMapping("/enrollments/{id}/attendance")
    public ResponseEntity<Map<String, Object>> updateAttendance(
            @PathVariable("id") String enrollmentId,
            @RequestBody Map<String, Integer> body) {

        int attended = body.getOrDefault("attended", 0);
        int total = body.getOrDefault("total", 0);

        Enrollment updated = enrollmentService.updateAttendance(enrollmentId, attended, total);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Attendance updated successfully");
        response.put("enrollment", updated);
        return ResponseEntity.ok(response);
    }
}
