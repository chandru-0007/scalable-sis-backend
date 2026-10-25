package com.sis.backend.controller;

import com.sis.backend.service.ParentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/parent")
@CrossOrigin(origins = "*")
public class ParentController {

    private final ParentService parentService;

    public ParentController(ParentService parentService) {
        this.parentService = parentService;
    }

    /**
     * Parent Academic Progress Report for a student.
     * GET /api/parent/student/{rollNumber}
     */
    @GetMapping("/student/{rollNumber}")
    public ResponseEntity<Map<String, Object>> getWardReport(@PathVariable String rollNumber) {
        try {
            Map<String, Object> report = parentService.getWardAcademicReport(rollNumber);
            report.put("status", "SUCCESS");
            return ResponseEntity.ok(report);
        } catch (IllegalArgumentException e) {
            Map<String, Object> err = new HashMap<>();
            err.put("status", "ERROR");
            err.put("message", e.getMessage());
            return ResponseEntity.status(404).body(err);
        }
    }

    /**
     * Parent verification gateway (checks if student roll number exists).
     * POST /api/parent/verify
     * Body: { "rollNumber": "22CS014" }
     */
    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyWard(@RequestBody Map<String, String> payload) {
        String rollNumber = payload.get("rollNumber");
        if (rollNumber == null || rollNumber.isBlank()) {
            Map<String, Object> err = new HashMap<>();
            err.put("status", "ERROR");
            err.put("message", "rollNumber is required.");
            return ResponseEntity.badRequest().body(err);
        }

        Map<String, Object> report = parentService.getWardAcademicReport(rollNumber);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "VERIFIED");
        response.put("studentName", report.get("studentName"));
        response.put("rollNumber", report.get("rollNumber"));
        response.put("department", report.get("department"));
        response.put("report", report);

        return ResponseEntity.ok(response);
    }
}
