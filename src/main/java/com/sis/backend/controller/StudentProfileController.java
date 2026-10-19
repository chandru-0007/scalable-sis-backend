package com.sis.backend.controller;

import com.sis.backend.model.Student;
import com.sis.backend.repository.StudentRepository;
import com.sis.backend.service.AuthSessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Student Profile Management Controller.
 * Supports viewing and updating student profiles, with automatic cache synchronization.
 */
@RestController
@RequestMapping("/api/students")
@CrossOrigin(origins = "*")
public class StudentProfileController {

    private final StudentRepository studentRepository;
    private final AuthSessionService authSessionService;

    public StudentProfileController(StudentRepository studentRepository,
                                    AuthSessionService authSessionService) {
        this.studentRepository = studentRepository;
        this.authSessionService = authSessionService;
    }

    /**
     * Get Student Profile by ID.
     * GET /api/students/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getProfile(@PathVariable String id) {
        Optional<Student> studentOpt = studentRepository.findById(id);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Student student = studentOpt.get();
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("student", sanitize(student));
        return ResponseEntity.ok(response);
    }

    /**
     * Update Student Profile (Department, Semester, Name).
     * PUT /api/students/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateProfile(
            @PathVariable String id,
            @RequestBody Map<String, Object> updates) {

        Optional<Student> studentOpt = studentRepository.findById(id);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Student student = studentOpt.get();

        if (updates.containsKey("name") && updates.get("name") != null) {
            student.setName(String.valueOf(updates.get("name")).trim());
        }
        if (updates.containsKey("department") && updates.get("department") != null) {
            student.setDepartment(String.valueOf(updates.get("department")).trim());
        }
        if (updates.containsKey("semester") && updates.get("semester") != null) {
            try {
                student.setSemester(Integer.parseInt(String.valueOf(updates.get("semester"))));
            } catch (NumberFormatException ignored) {
            }
        }

        Student saved = studentRepository.save(student);

        // Update active session cache in Redis
        authSessionService.createSession(saved);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Profile updated successfully");
        response.put("student", sanitize(saved));
        return ResponseEntity.ok(response);
    }

    /**
     * List all students (limited).
     * GET /api/students
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listStudents(
            @RequestParam(defaultValue = "50") int limit) {

        List<Student> students = studentRepository.findAll();
        List<Map<String, Object>> sanitized = students.stream()
                .limit(limit)
                .map(this::sanitize)
                .toList();

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("count", sanitized.size());
        response.put("students", sanitized);
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> sanitize(Student s) {
        Map<String, Object> map = new HashMap<>();
        map.put("studentId", s.getId());
        map.put("rollNumber", s.getRollNumber());
        map.put("name", s.getName());
        map.put("email", s.getEmail());
        map.put("department", s.getDepartment());
        map.put("semester", s.getSemester());
        map.put("targetShard", s.getTargetShard());
        map.put("createdAt", s.getCreatedAt());
        return map;
    }
}
