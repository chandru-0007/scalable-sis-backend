package com.sis.backend.controller;

import com.sis.backend.model.AcademicNotice;
import com.sis.backend.model.Course;
import com.sis.backend.service.AdminService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /**
     * Admin/Registrar onboards a new faculty member.
     * POST /api/admin/faculty
     */
    @PostMapping("/faculty")
    public ResponseEntity<com.sis.backend.model.Student> addFaculty(
            @RequestBody com.sis.backend.model.RegisterRequest request) {
        com.sis.backend.model.Student faculty = adminService.addFaculty(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(faculty);
    }

    /**
     * Get all faculty members.
     * GET /api/admin/faculty
     */
    @GetMapping("/faculty")
    public ResponseEntity<List<com.sis.backend.model.Student>> getAllFaculty() {
        return ResponseEntity.ok(adminService.getAllFaculty());
    }

    /**
     * Assign or reassign a faculty instructor to a course.
     * PUT /api/admin/courses/{code}/assign-faculty
     */
    @PutMapping("/courses/{code}/assign-faculty")
    public ResponseEntity<Map<String, Object>> assignFaculty(
            @PathVariable String code,
            @RequestParam(required = false) String facultyName,
            @RequestParam(required = false) String facultyId,
            @RequestParam(required = false) String room,
            @RequestParam(required = false) String schedule,
            @RequestBody(required = false) Map<String, String> body) {

        String fName = facultyName;
        String fId = facultyId;
        String fRoom = room;
        String fSched = schedule;

        if (body != null) {
            if (fName == null || fName.isBlank()) fName = body.get("facultyName");
            if (fId == null || fId.isBlank()) fId = body.get("facultyId");
            if (fRoom == null || fRoom.isBlank()) fRoom = body.get("room");
            if (fSched == null || fSched.isBlank()) fSched = body.get("schedule");
        }

        Course updated = adminService.assignFacultyToCourse(code, fName, fId, fRoom, fSched);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Faculty successfully assigned/reassigned to course " + code);
        response.put("course", updated);
        return ResponseEntity.ok(response);
    }

    /**
     * Reschedule Course timing or room with student timetable cache eviction & auto-notice.
     * PUT /api/admin/courses/{code}/reschedule
     */
    @PutMapping("/courses/{code}/reschedule")
    public ResponseEntity<Map<String, Object>> rescheduleCourse(
            @PathVariable String code,
            @RequestBody Map<String, String> body) {

        String newSchedule = body.get("newSchedule");
        String newRoom = body.get("newRoom");
        String reason = body.get("reason");

        Course updated = adminService.rescheduleCourse(code, newSchedule, newRoom, reason);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Course " + code + " successfully rescheduled.");
        response.put("course", updated);
        return ResponseEntity.ok(response);
    }

    /**
     * Pre-scheduling collision check for rooms and faculty.
     * POST /api/admin/courses/check-schedule-conflict
     */
    @PostMapping("/courses/check-schedule-conflict")
    public ResponseEntity<Map<String, Object>> checkConflict(@RequestBody Map<String, String> body) {
        String schedule = body.get("schedule");
        String room = body.get("room");
        String facultyName = body.get("facultyName");
        String excludeCode = body.get("excludeCourseCode");

        Map<String, Object> report = adminService.checkScheduleConflict(schedule, room, facultyName, excludeCode);
        return ResponseEntity.ok(report);
    }

    /**
     * Update Course Semester, Academic Year, Term, and Lifecycle Status.
     * PUT /api/admin/courses/{code}/period
     */
    @PutMapping("/courses/{code}/period")
    public ResponseEntity<Map<String, Object>> updateCoursePeriod(
            @PathVariable String code,
            @RequestBody Map<String, Object> body) {

        int semester = body.get("semester") != null ? Integer.parseInt(String.valueOf(body.get("semester"))) : 0;
        String academicYear = (String) body.get("academicYear");
        String term = (String) body.get("term");
        String startDate = (String) body.get("startDate");
        String endDate = (String) body.get("endDate");
        String status = (String) body.get("status");

        Course updated = adminService.updateCoursePeriodAndSemester(code, semester, academicYear, term, startDate, endDate, status);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Course " + code + " academic period and lifecycle updated.");
        response.put("course", updated);
        return ResponseEntity.ok(response);
    }

    /**
     * Get faculty workload & allocation analytics.
     * GET /api/admin/faculty/workload
     */
    @GetMapping("/faculty/workload")
    public ResponseEntity<List<Map<String, Object>>> getFacultyWorkload() {
        return ResponseEntity.ok(adminService.getFacultyWorkload());
    }

    /**
     * Create a new course.
     * POST /api/admin/courses
     */
    @PostMapping("/courses")
    public ResponseEntity<Course> createCourse(@RequestBody Course course) {
        Course created = adminService.createCourse(course);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Dynamically update course seat capacity.
     * PUT /api/admin/courses/{code}/capacity?capacity=75
     */
    @PutMapping("/courses/{code}/capacity")
    public ResponseEntity<Map<String, Object>> updateCapacity(
            @PathVariable String code,
            @RequestParam int capacity) {

        Course updated = adminService.updateCourseCapacity(code, capacity);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Course capacity updated to " + capacity);
        response.put("course", updated);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete course.
     * DELETE /api/admin/courses/{code}
     */
    @DeleteMapping("/courses/{code}")
    public ResponseEntity<Map<String, Object>> deleteCourse(@PathVariable String code) {
        boolean deleted = adminService.deleteCourse(code);
        Map<String, Object> response = new HashMap<>();
        if (deleted) {
            response.put("status", "SUCCESS");
            response.put("message", "Course " + code + " deleted successfully.");
            return ResponseEntity.ok(response);
        } else {
            response.put("status", "NOT_FOUND");
            response.put("message", "Course not found with code: " + code);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    /**
     * University-wide Attendance Cutoff Compliance Report.
     * GET /api/admin/reports/attendance-cutoff?minPercentage=75
     * GET /api/admin/attendance/debarred-report
     */
    @GetMapping({"/reports/attendance-cutoff", "/attendance/debarred-report"})
    public ResponseEntity<Map<String, Object>> getAttendanceCutoffReport(
            @RequestParam(defaultValue = "75.0") double minPercentage) {
        return ResponseEntity.ok(adminService.getAttendanceCutoffReport(minPercentage));
    }

    /**
     * Academic Leadership & Registrar Overview Metrics.
     * GET /api/admin/reports/overview
     */
    @GetMapping("/reports/overview")
    public ResponseEntity<Map<String, Object>> getOverview() {
        return ResponseEntity.ok(adminService.getUniversityOverview());
    }

    /**
     * Publish Official Academic Announcement with automatic Base62 Short URL.
     * POST /api/admin/notices
     */
    @PostMapping("/notices")
    public ResponseEntity<AcademicNotice> publishNotice(@RequestBody Map<String, String> payload) {
        String title = payload.getOrDefault("title", "Official Announcement");
        String content = payload.getOrDefault("content", "");
        String author = payload.getOrDefault("author", "Office of the Registrar");
        String externalUrl = payload.get("externalUrl");

        AcademicNotice notice = adminService.publishOfficialNotice(title, content, author, externalUrl);
        return ResponseEntity.status(HttpStatus.CREATED).body(notice);
    }
}
