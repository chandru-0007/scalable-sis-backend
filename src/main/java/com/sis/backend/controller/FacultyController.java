package com.sis.backend.controller;

import com.sis.backend.model.AcademicNotice;
import com.sis.backend.model.Course;
import com.sis.backend.model.Enrollment;
import com.sis.backend.service.FacultyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/faculty")
@CrossOrigin(origins = "*")
public class FacultyController {

    private final FacultyService facultyService;
    private final com.sis.backend.service.EnrollmentService enrollmentService;

    public FacultyController(FacultyService facultyService,
                             com.sis.backend.service.EnrollmentService enrollmentService) {
        this.facultyService = facultyService;
        this.enrollmentService = enrollmentService;
    }

    /**
     * Get Timetable for Faculty Teaching Schedule.
     * GET /api/faculty/timetable?facultyId=FAC001
     */
    @GetMapping("/timetable")
    public ResponseEntity<Map<String, Object>> getFacultyTimetable(
            @RequestParam(required = false) String facultyId,
            @RequestParam(required = false) String facultyName) {
        String identifier = (facultyId != null && !facultyId.isBlank()) ? facultyId : "FAC001";
        List<com.sis.backend.model.TimetableEntry> timetable = enrollmentService.getFacultyTimetable(identifier, facultyName);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("facultyId", identifier);
        response.put("slotsCount", timetable.size());
        response.put("timetable", timetable);
        return ResponseEntity.ok(response);
    }

    /**
     * Get courses assigned to a faculty member.
     * GET /api/faculty/courses?facultyId=FAC001
     * GET /api/faculty/courses?facultyName=Ramanathan&department=CSE
     */
    @GetMapping("/courses")
    public ResponseEntity<List<Course>> getAssignedCourses(
            @RequestParam(required = false) String facultyName,
            @RequestParam(required = false) String facultyId,
            @RequestParam(required = false) String department) {
        return ResponseEntity.ok(facultyService.getCoursesForFaculty(facultyName, facultyId, department));
    }

    /**
     * Get class roster of enrolled students for a specific course.
     * GET /api/faculty/courses/{courseCode}/roster
     */
    @GetMapping("/courses/{courseCode}/roster")
    public ResponseEntity<Map<String, Object>> getCourseRoster(@PathVariable String courseCode) {
        List<Map<String, Object>> roster = facultyService.getCourseRoster(courseCode);
        Map<String, Object> response = new HashMap<>();
        response.put("courseCode", courseCode.toUpperCase());
        response.put("totalEnrolled", roster.size());
        response.put("roster", roster);
        return ResponseEntity.ok(response);
    }

    /**
     * Batch attendance submission for a course.
     * POST /api/faculty/courses/{courseCode}/attendance
     * Body: [ { "studentId": "...", "attended": true }, ... ]
     */
    @PostMapping("/courses/{courseCode}/attendance")
    public ResponseEntity<Map<String, Object>> submitAttendance(
            @PathVariable String courseCode,
            @RequestBody List<Map<String, Object>> attendanceRecords) {

        int updated = facultyService.markAttendance(courseCode, attendanceRecords);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("courseCode", courseCode.toUpperCase());
        response.put("studentsUpdated", updated);
        response.put("message", "Attendance recorded and student Redis cache invalidated successfully.");
        return ResponseEntity.ok(response);
    }

    /**
     * Submit or update student examination grade.
     * PUT /api/faculty/courses/{courseCode}/grades
     * Body: { "enrollmentId": "...", "grade": "A+", "gradePoint": 9.0 }
     */
    @PutMapping("/courses/{courseCode}/grades")
    public ResponseEntity<Map<String, Object>> submitGrade(
            @PathVariable String courseCode,
            @RequestBody Map<String, Object> payload) {

        String enrollmentId = (String) payload.get("enrollmentId");
        String grade = (String) payload.get("grade");
        Number gp = (Number) payload.getOrDefault("gradePoint", 0.0);
        Number marks = (Number) payload.get("marks");
        String remarks = (String) payload.get("remarks");

        if (enrollmentId == null || grade == null) {
            Map<String, Object> err = new HashMap<>();
            err.put("status", "ERROR");
            err.put("message", "enrollmentId and grade are required.");
            return ResponseEntity.badRequest().body(err);
        }

        Enrollment updated = facultyService.submitGrade(
                enrollmentId,
                grade,
                gp.doubleValue(),
                marks != null ? marks.doubleValue() : null,
                remarks
        );

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Grade and result published successfully.");
        response.put("enrollment", updated);
        return ResponseEntity.ok(response);
    }

    /**
     * Batch publish all student grades and final course results.
     * POST /api/faculty/courses/{courseCode}/publish-results
     * Body: { "grades": [ { "enrollmentId": "...", "grade": "A+", "gradePoint": 9.0, "marks": 92.0, "remarks": "..." } ], "facultyName": "..." }
     */
    @PostMapping("/courses/{courseCode}/publish-results")
    public ResponseEntity<Map<String, Object>> publishCourseResults(
            @PathVariable String courseCode,
            @RequestBody(required = false) Map<String, Object> payload) {

        List<Map<String, Object>> gradesList = null;
        String facultyName = null;
        if (payload != null) {
            gradesList = (List<Map<String, Object>>) payload.get("grades");
            facultyName = (String) payload.get("facultyName");
        }

        Map<String, Object> result = facultyService.publishCourseResults(courseCode, gradesList, facultyName);
        return ResponseEntity.ok(result);
    }

    /**
     * Post a course-level announcement for enrolled students.
     * POST /api/faculty/announcements
     * Body: { "courseCode": "CS301", "title": "Extra Class", "content": "Thursday 4:30 PM", "author": "Dr. Ramanathan" }
     */
    @PostMapping("/announcements")
    public ResponseEntity<AcademicNotice> postAnnouncement(@RequestBody Map<String, String> payload) {
        String courseCode = payload.getOrDefault("courseCode", "GENERAL");
        String title = payload.getOrDefault("title", "Course Announcement");
        String content = payload.getOrDefault("content", "");
        String author = payload.getOrDefault("author", "Course Instructor");

        AcademicNotice notice = facultyService.postCourseAnnouncement(courseCode, title, content, author);
        return ResponseEntity.status(HttpStatus.CREATED).body(notice);
    }
}
