package com.sis.backend.controller;

import com.sis.backend.model.AcademicNotice;
import com.sis.backend.model.Course;
import com.sis.backend.service.AdminService;
import com.sis.backend.service.CourseService;
import com.sis.backend.service.EnrollmentService;
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
    private final CourseService courseService;
    private final EnrollmentService enrollmentService;

    public AdminController(AdminService adminService,
                           CourseService courseService,
                           EnrollmentService enrollmentService) {
        this.adminService = adminService;
        this.courseService = courseService;
        this.enrollmentService = enrollmentService;
    }

    /**
     * Admin onboards a new user (Student or Faculty) with auto-generated credentials sent via email.
     * POST /api/admin/users
     */
    @PostMapping("/users")
    public ResponseEntity<Map<String, Object>> addUser(
            @RequestBody com.sis.backend.model.RegisterRequest request) {
        Map<String, Object> result = adminService.addUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
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

        Map<String, Object> response = adminService.rescheduleCourseWithReport(code, newSchedule, newRoom, reason);
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
     */
    @GetMapping("/reports/attendance-cutoff")
    public ResponseEntity<Map<String, Object>> getAttendanceCutoffReport(
            @RequestParam(defaultValue = "75.0") double minPercentage) {
        return ResponseEntity.ok(adminService.getAttendanceCutoffReport(minPercentage));
    }

    /**
     * Debarred Students report matching frontend contract.
     * GET /api/admin/attendance/debarred-report
     */
    @GetMapping("/attendance/debarred-report")
    public ResponseEntity<Map<String, Object>> getDebarredReport(
            @RequestParam(defaultValue = "75.0") double minPercentage) {
        Map<String, Object> rawReport = adminService.getAttendanceCutoffReport(minPercentage);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("cutoffPercentage", minPercentage);
        response.put("totalDebarred", rawReport.get("totalDebarred"));
        response.put("totalEligible", rawReport.get("totalEligible"));
        response.put("complianceRate", rawReport.get("complianceRate"));
        response.put("debarredStudents", rawReport.get("flaggedRecords"));
        return ResponseEntity.ok(response);
    }

    /**
     * Academic Leadership & Registrar Overview Metrics.
     * GET /api/admin/overview or /api/admin/reports/overview
     */
    @GetMapping({"/overview", "/reports/overview"})
    public ResponseEntity<Map<String, Object>> getOverview() {
        return ResponseEntity.ok(adminService.getUniversityOverview());
    }

    /**
     * Get all enrolled students for Admin directory & overrides.
     * GET /api/admin/students
     */
    @GetMapping("/students")
    public ResponseEntity<Map<String, Object>> getAdminStudents() {
        List<com.sis.backend.model.Student> students = adminService.getAllStudents();
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("count", students.size());
        response.put("students", students);
        return ResponseEntity.ok(response);
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

    /**
     * Get all courses for Admin management.
     * GET /api/admin/courses
     */
    @GetMapping("/courses")
    public ResponseEntity<List<Course>> getAllCourses() {
        return ResponseEntity.ok(courseService.getAllCourses());
    }

    /**
     * Admin Direct Course Enrollment Override.
     * POST /api/admin/students/enroll
     */
    @PostMapping("/students/enroll")
    public ResponseEntity<Map<String, Object>> adminEnrollStudent(@RequestBody Map<String, String> body) {
        String studentIdentifier = body.get("studentId");
        if (studentIdentifier == null || studentIdentifier.isBlank()) {
            studentIdentifier = body.get("rollNumber");
        }
        String courseCode = body.get("courseCode");

        if (studentIdentifier == null || courseCode == null) {
            Map<String, Object> err = new HashMap<>();
            err.put("status", "ERROR");
            err.put("message", "studentId/rollNumber and courseCode are required.");
            return ResponseEntity.badRequest().body(err);
        }

        try {
            var enrollment = enrollmentService.enroll(studentIdentifier, courseCode);
            Map<String, Object> res = new HashMap<>();
            res.put("status", "SUCCESS");
            res.put("message", "Student " + studentIdentifier + " enrolled in " + courseCode + " by Admin Override.");
            res.put("enrollment", enrollment);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("status", "ERROR");
            err.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        }
    }

    /**
     * Admin Direct Course Drop Override.
     * DELETE /api/admin/students/enroll
     */
    @DeleteMapping("/students/enroll")
    public ResponseEntity<Map<String, Object>> adminDropStudent(@RequestBody Map<String, String> body) {
        String studentIdentifier = body.get("studentId");
        if (studentIdentifier == null || studentIdentifier.isBlank()) {
            studentIdentifier = body.get("rollNumber");
        }
        String courseCode = body.get("courseCode");

        boolean dropped = enrollmentService.unenroll(studentIdentifier, courseCode);
        Map<String, Object> res = new HashMap<>();
        res.put("status", dropped ? "SUCCESS" : "ERROR");
        res.put("message", dropped ? "Student " + studentIdentifier + " unenrolled from " + courseCode : "Enrollment not found.");
        return ResponseEntity.ok(res);
    }
}
