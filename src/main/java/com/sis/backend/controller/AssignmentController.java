package com.sis.backend.controller;

import com.sis.backend.model.Assignment;
import com.sis.backend.model.AssignmentSubmission;
import com.sis.backend.service.AssignmentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/assignments")
@CrossOrigin(origins = "*")
public class AssignmentController {

    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    /**
     * Faculty posts a new assignment.
     * POST /api/assignments
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createAssignment(@RequestBody Map<String, Object> body) {
        String courseCode = (String) body.get("courseCode");
        String title = (String) body.get("title");
        String description = (String) body.get("description");
        Number maxMarks = (Number) body.getOrDefault("maxMarks", 100);
        Number dueDate = (Number) body.getOrDefault("dueDate", 0);
        String facultyId = (String) body.get("facultyId");
        String facultyName = (String) body.get("facultyName");

        if (courseCode == null || title == null || title.isBlank()) {
            Map<String, Object> err = new HashMap<>();
            err.put("status", "ERROR");
            err.put("message", "courseCode and title are required.");
            return ResponseEntity.badRequest().body(err);
        }

        Assignment created = assignmentService.createAssignment(
                courseCode,
                title,
                description,
                maxMarks != null ? maxMarks.intValue() : 100,
                dueDate != null ? dueDate.longValue() : 0L,
                facultyId,
                facultyName
        );

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Assignment posted successfully for course " + courseCode);
        response.put("assignment", created);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get all assignments for a course.
     * GET /api/assignments/course/{courseCode}
     */
    @GetMapping("/course/{courseCode}")
    public ResponseEntity<List<Assignment>> getAssignmentsByCourse(@PathVariable String courseCode) {
        return ResponseEntity.ok(assignmentService.getAssignmentsByCourse(courseCode));
    }

    /**
     * Get all assignments relevant to an enrolled student (with submission status).
     * GET /api/assignments/student/{studentId}
     */
    @GetMapping("/student/{studentId}")
    public ResponseEntity<List<Map<String, Object>>> getAssignmentsForStudent(@PathVariable String studentId) {
        return ResponseEntity.ok(assignmentService.getAssignmentsForStudent(studentId));
    }

    /**
     * Student answers/submits an assignment.
     * POST /api/assignments/{assignmentId}/submit
     */
    @PostMapping("/{assignmentId}/submit")
    public ResponseEntity<Map<String, Object>> submitAssignment(
            @PathVariable String assignmentId,
            @RequestBody Map<String, String> body) {

        String studentId = body.get("studentId");
        String content = body.get("content");
        String attachmentUrl = body.get("attachmentUrl");

        if (studentId == null || content == null || content.isBlank()) {
            Map<String, Object> err = new HashMap<>();
            err.put("status", "ERROR");
            err.put("message", "studentId and content are required for submission.");
            return ResponseEntity.badRequest().body(err);
        }

        try {
            AssignmentSubmission submission = assignmentService.submitAssignment(
                    assignmentId, studentId, content, attachmentUrl
            );

            Map<String, Object> response = new HashMap<>();
            response.put("status", "SUCCESS");
            response.put("message", "Assignment submitted successfully!");
            response.put("submission", submission);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> err = new HashMap<>();
            err.put("status", "ERROR");
            err.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        }
    }

    /**
     * Faculty retrieves submissions for an assignment.
     * GET /api/assignments/{assignmentId}/submissions
     */
    @GetMapping("/{assignmentId}/submissions")
    public ResponseEntity<List<AssignmentSubmission>> getSubmissions(@PathVariable String assignmentId) {
        return ResponseEntity.ok(assignmentService.getSubmissionsForAssignment(assignmentId));
    }

    /**
     * Faculty evaluates a student submission.
     * POST /api/assignments/{assignmentId}/evaluate
     */
    @PostMapping("/{assignmentId}/evaluate")
    public ResponseEntity<Map<String, Object>> evaluateSubmission(
            @PathVariable String assignmentId,
            @RequestBody Map<String, Object> body) {

        String submissionId = (String) body.get("submissionId");
        Number marks = (Number) body.get("marksObtained");
        String feedback = (String) body.get("facultyFeedback");
        String facultyName = (String) body.get("facultyName");

        if (submissionId == null || marks == null) {
            Map<String, Object> err = new HashMap<>();
            err.put("status", "ERROR");
            err.put("message", "submissionId and marksObtained are required.");
            return ResponseEntity.badRequest().body(err);
        }

        try {
            AssignmentSubmission evaluated = assignmentService.evaluateSubmission(
                    submissionId, marks.doubleValue(), feedback, facultyName
            );

            Map<String, Object> response = new HashMap<>();
            response.put("status", "SUCCESS");
            response.put("message", "Submission evaluated successfully!");
            response.put("submission", evaluated);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> err = new HashMap<>();
            err.put("status", "ERROR");
            err.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        }
    }

    /**
     * Delete an assignment.
     * DELETE /api/assignments/{assignmentId}
     */
    @DeleteMapping("/{assignmentId}")
    public ResponseEntity<Map<String, Object>> deleteAssignment(@PathVariable String assignmentId) {
        boolean deleted = assignmentService.deleteAssignment(assignmentId);
        Map<String, Object> response = new HashMap<>();
        response.put("status", deleted ? "SUCCESS" : "ERROR");
        response.put("message", deleted ? "Assignment deleted" : "Assignment not found");
        return ResponseEntity.ok(response);
    }
}
