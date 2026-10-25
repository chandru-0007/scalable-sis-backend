package com.sis.backend.service;

import com.sis.backend.core.autocomplete.TrieAutocompleteService;
import com.sis.backend.core.hashing.ConsistentHashRing;
import com.sis.backend.core.id.SnowflakeIdGenerator;
import com.sis.backend.core.kv.KeyValueCacheService;
import com.sis.backend.core.url.UrlShortenerService;
import com.sis.backend.model.AcademicNotice;
import com.sis.backend.model.Course;
import com.sis.backend.model.Enrollment;
import com.sis.backend.model.Student;
import com.sis.backend.repository.CourseRepository;
import com.sis.backend.repository.EnrollmentRepository;
import com.sis.backend.repository.NoticeRepository;
import com.sis.backend.repository.StudentRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AdminService {

    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StudentRepository studentRepository;
    private final NoticeRepository noticeRepository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final KeyValueCacheService keyValueCacheService;
    private final TrieAutocompleteService trieAutocompleteService;
    private final UrlShortenerService urlShortenerService;

    private final ConsistentHashRing consistentHashRing;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final com.sis.backend.core.realtime.RealtimeEventService realtimeEventService;

    public AdminService(CourseRepository courseRepository,
                        EnrollmentRepository enrollmentRepository,
                        StudentRepository studentRepository,
                        NoticeRepository noticeRepository,
                        SnowflakeIdGenerator snowflakeIdGenerator,
                        KeyValueCacheService keyValueCacheService,
                        TrieAutocompleteService trieAutocompleteService,
                        UrlShortenerService urlShortenerService,
                        ConsistentHashRing consistentHashRing,
                        org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
                        com.sis.backend.core.realtime.RealtimeEventService realtimeEventService) {
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.studentRepository = studentRepository;
        this.noticeRepository = noticeRepository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.keyValueCacheService = keyValueCacheService;
        this.trieAutocompleteService = trieAutocompleteService;
        this.urlShortenerService = urlShortenerService;
        this.consistentHashRing = consistentHashRing;
        this.passwordEncoder = passwordEncoder;
        this.realtimeEventService = realtimeEventService;
    }

    /**
     * Admin/Registrar onboards a new faculty member.
     */
    public Student addFaculty(com.sis.backend.model.RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        if (studentRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("User with email " + email + " already exists.");
        }
        if (studentRepository.existsByRollNumber(request.getRollNumber().trim())) {
            throw new IllegalArgumentException("Faculty with ID " + request.getRollNumber() + " already exists.");
        }

        String facultyId = snowflakeIdGenerator.nextIdStr();
        String targetShard = consistentHashRing.getShard(facultyId);
        String passHash = passwordEncoder.encode(request.getPassword() != null ? request.getPassword() : "Faculty@123");

        Student faculty = new Student(
                facultyId,
                request.getRollNumber().trim(),
                request.getName().trim(),
                email,
                passHash,
                request.getDepartment() != null ? request.getDepartment().trim() : "CSE",
                0,
                "FACULTY",
                targetShard,
                System.currentTimeMillis()
        );

        return studentRepository.save(faculty);
    }

    /**
     * Get list of all faculty members in the university.
     */
    public List<Student> getAllFaculty() {
        return studentRepository.findAll().stream()
                .filter(s -> "FACULTY".equalsIgnoreCase(s.getRole()))
                .toList();
    }

    /**
     * Admin/Registrar/HOD assigns or reassigns a faculty instructor to a course.
     * Records instructor reassignment history, updates Prefix Trie, and evicts course/timetable caches.
     */
    public Course assignFacultyToCourse(String courseCode, String facultyName, String facultyId, String room, String schedule) {
        String code = courseCode.trim().toUpperCase();
        Course course = courseRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Course not found: " + code));

        // Track previous instructor reassignment history
        if (facultyName != null && !facultyName.isBlank()) {
            String oldInstructor = course.getInstructor();
            if (oldInstructor != null && !oldInstructor.isBlank() && !oldInstructor.equalsIgnoreCase(facultyName.trim())) {
                String changeLog = "Reassigned on " + java.time.LocalDate.now() + ": [" + oldInstructor + "] -> [" + facultyName.trim() + "]";
                course.getPreviousFacultyHistory().add(changeLog);
            }
            course.setInstructor(facultyName.trim());
        }

        if (facultyId != null && !facultyId.isBlank()) {
            course.setFacultyId(facultyId.trim());
        }
        if (room != null && !room.isBlank()) {
            course.setRoom(room.trim());
        }
        if (schedule != null && !schedule.isBlank()) {
            course.setSchedule(schedule.trim());
        }

        Course saved = courseRepository.save(course);

        // Invalidate Redis Course Catalog Cache
        keyValueCacheService.evict("cache:courses:all");

        // Invalidate Timetable Caches for all enrolled students so new instructor is visible
        List<Enrollment> enrolledList = enrollmentRepository.findByCourseCode(code);
        for (Enrollment e : enrolledList) {
            keyValueCacheService.evict("cache:timetable:" + e.getStudentId());
        }

        return saved;
    }

    /**
     * Reschedule Course Timing or Classroom.
     * 1. Records change into rescheduleHistory audit trail.
     * 2. Evicts Redis course catalog cache.
     * 3. Scatter-gather invalidates timetable caches for ALL enrolled students.
     * 4. Automatically publishes an official Academic Notice with a Base62 Short URL.
     */
    public Course rescheduleCourse(String courseCode, String newSchedule, String newRoom, String reason) {
        Map<String, Object> report = rescheduleCourseWithReport(courseCode, newSchedule, newRoom, reason);
        return (Course) report.get("course");
    }

    public Map<String, Object> rescheduleCourseWithReport(String courseCode, String newSchedule, String newRoom, String reason) {
        String code = courseCode.trim().toUpperCase();
        Course course = courseRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Course not found: " + code));

        String oldSchedule = course.getSchedule() != null ? course.getSchedule() : "TBD";
        String oldRoom = course.getRoom() != null ? course.getRoom() : "TBD";

        if (newSchedule != null && !newSchedule.isBlank()) {
            course.setSchedule(newSchedule.trim());
        }
        if (newRoom != null && !newRoom.isBlank()) {
            course.setRoom(newRoom.trim());
        }

        String logEntry = "Rescheduled on " + java.time.LocalDate.now() + " from [" + oldSchedule + " @ " + oldRoom +
                "] to [" + course.getSchedule() + " @ " + course.getRoom() + "]. Reason: " +
                (reason != null && !reason.isBlank() ? reason.trim() : "Operational adjustment");
        course.getRescheduleHistory().add(logEntry);

        Course saved = courseRepository.save(course);

        // 1. Evict course catalog cache
        keyValueCacheService.evict("cache:courses:all");

        // 2. Invalidate timetable caches for all enrolled students
        List<Enrollment> enrolledList = enrollmentRepository.findByCourseCode(code);
        for (Enrollment e : enrolledList) {
            keyValueCacheService.evict("cache:timetable:" + e.getStudentId());
        }

        // 3. Auto-publish official academic circular with Base62 Short URL
        String noticeTitle = "[SCHEDULE UPDATE] " + course.getCode() + " " + course.getTitle() + " Rescheduled";
        String noticeContent = "Attention enrolled students: The lecture schedule for " + course.getCode() +
                " (" + course.getTitle() + ") has been rescheduled to " + course.getSchedule() +
                " in room " + course.getRoom() + ". Reason: " +
                (reason != null && !reason.isBlank() ? reason.trim() : "Timetable optimization") +
                ". Please check your updated student timetable portal.";
        AcademicNotice notice = publishOfficialNotice(noticeTitle, noticeContent, "Office of the Registrar", "https://university.ac.in/courses/" + course.getCode().toLowerCase());

        // 4. Real-Time Broadcast: Broadcast high-priority reschedule alert to all connected students/parents
        String shortUrl = notice != null ? notice.getShortUrl() : "";
        realtimeEventService.emitReschedule(code, course.getSchedule(), course.getRoom(), reason, shortUrl);

        Map<String, Object> report = new HashMap<>();
        report.put("status", "SUCCESS");
        report.put("message", "Course " + code + " successfully rescheduled.");
        report.put("course", saved);
        report.put("invalidatedStudentCaches", enrolledList.size());
        report.put("courseCacheStatus", "Cache Invalidation Purged (TTL Refreshed)");
        report.put("broadcastCircular", noticeTitle);
        report.put("shortUrl", shortUrl);
        return report;
    }

    /**
     * Check for classroom or instructor schedule collisions before confirming a timetable.
     */
    public Map<String, Object> checkScheduleConflict(String schedule, String room, String facultyName, String excludeCourseCode) {
        List<Course> allCourses = courseRepository.findAll();
        List<String> conflicts = new ArrayList<>();

        for (Course c : allCourses) {
            if (excludeCourseCode != null && excludeCourseCode.equalsIgnoreCase(c.getCode())) {
                continue;
            }
            // Room collision check: same room and overlapping schedule
            if (room != null && !room.isBlank() && c.getRoom() != null && c.getRoom().equalsIgnoreCase(room.trim())) {
                if (schedule != null && !schedule.isBlank() && c.getSchedule() != null && isScheduleOverlap(schedule, c.getSchedule())) {
                    conflicts.add("Room collision: " + room + " is already booked by " + c.getCode() + " (" + c.getTitle() + ") at " + c.getSchedule());
                }
            }
            // Faculty collision check: same faculty and overlapping schedule
            if (facultyName != null && !facultyName.isBlank() && c.getInstructor() != null && c.getInstructor().equalsIgnoreCase(facultyName.trim())) {
                if (schedule != null && !schedule.isBlank() && c.getSchedule() != null && isScheduleOverlap(schedule, c.getSchedule())) {
                    conflicts.add("Faculty collision: " + facultyName + " is already teaching " + c.getCode() + " (" + c.getTitle() + ") at " + c.getSchedule());
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("hasConflict", !conflicts.isEmpty());
        result.put("conflictCount", conflicts.size());
        result.put("conflicts", conflicts);
        return result;
    }

    private boolean isScheduleOverlap(String sched1, String sched2) {
        if (sched1 == null || sched2 == null) return false;
        String s1 = sched1.toLowerCase().trim();
        String s2 = sched2.toLowerCase().trim();
        if (s1.equals(s2)) return true;

        String[] days = {"mon", "tue", "wed", "thu", "fri", "sat"};
        for (String d : days) {
            if (s1.contains(d) && s2.contains(d)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Update Course Academic Period & Semester metadata.
     */
    public Course updateCoursePeriodAndSemester(String courseCode, int semester, String academicYear, String term,
                                                String startDate, String endDate, String status) {
        String code = courseCode.trim().toUpperCase();
        Course course = courseRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Course not found: " + code));

        if (semester > 0) course.setSemester(semester);
        if (academicYear != null && !academicYear.isBlank()) course.setAcademicYear(academicYear.trim());
        if (term != null && !term.isBlank()) course.setTerm(term.trim());
        if (startDate != null && !startDate.isBlank()) course.setStartDate(startDate.trim());
        if (endDate != null && !endDate.isBlank()) course.setEndDate(endDate.trim());
        if (status != null && !status.isBlank()) course.setStatus(status.trim().toUpperCase());

        Course saved = courseRepository.save(course);
        keyValueCacheService.evict("cache:courses:all");
        return saved;
    }

    /**
     * Get workload report showing each faculty member and their course assignments.
     */
    public List<Map<String, Object>> getFacultyWorkload() {
        List<Student> facultyList = getAllFaculty();
        List<Course> allCourses = courseRepository.findAll();
        List<Map<String, Object>> workloadList = new ArrayList<>();

        for (Student f : facultyList) {
            Map<String, Object> map = new HashMap<>();
            map.put("facultyId", f.getId());
            map.put("facultyCode", f.getRollNumber());
            map.put("name", f.getName());
            map.put("email", f.getEmail());
            map.put("department", f.getDepartment());

            List<Course> assigned = allCourses.stream()
                    .filter(c -> c.getInstructor() != null && c.getInstructor().toLowerCase().contains(f.getName().toLowerCase()))
                    .toList();

            int totalEnrolledStudents = assigned.stream().mapToInt(Course::getEnrolled).sum();

            map.put("coursesAssigned", assigned);
            map.put("totalCoursesCount", assigned.size());
            map.put("totalStudentsTaught", totalEnrolledStudents);
            workloadList.add(map);
        }

        return workloadList;
    }

    /**
     * Create a new academic course.
     * Generates a 64-bit Snowflake ID and indexes it immediately into the Prefix Trie.
     */
    public Course createCourse(Course course) {
        String code = course.getCode().trim().toUpperCase();
        if (courseRepository.findByCode(code).isPresent()) {
            throw new IllegalArgumentException("Course with code " + code + " already exists.");
        }

        course.setId(snowflakeIdGenerator.nextIdStr());
        course.setCode(code);
        if (course.getCapacity() <= 0) {
            course.setCapacity(60);
        }
        course.setEnrolled(0);
        if (course.getSemester() <= 0) {
            course.setSemester(1);
        }
        if (course.getAcademicYear() == null || course.getAcademicYear().isBlank()) {
            course.setAcademicYear("2026-2027");
        }
        if (course.getTerm() == null || course.getTerm().isBlank()) {
            course.setTerm("Odd Semester (Fall 2026)");
        }
        if (course.getStatus() == null || course.getStatus().isBlank()) {
            course.setStatus("ACTIVE");
        }

        Course saved = courseRepository.save(course);

        // Index in Prefix Trie for instant sub-millisecond search
        trieAutocompleteService.insert(saved.getCode(), saved.getCode(), saved.getTitle());
        trieAutocompleteService.insert(saved.getTitle(), saved.getCode(), saved.getTitle());

        // Invalidate course catalog cache in Redis Key-Value Store
        keyValueCacheService.evict("cache:courses:all");
        return saved;
    }

    /**
     * Dynamically update course seat capacity during registration rush periods.
     */
    public Course updateCourseCapacity(String courseCode, int newCapacity) {
        String code = courseCode.trim().toUpperCase();
        Course course = courseRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Course not found: " + code));

        if (newCapacity < course.getEnrolled()) {
            throw new IllegalArgumentException("New capacity (" + newCapacity + ") cannot be less than currently enrolled students (" + course.getEnrolled() + ")");
        }

        course.setCapacity(newCapacity);
        Course saved = courseRepository.save(course);
        keyValueCacheService.evict("cache:courses:all");
        return saved;
    }

    /**
     * Delete/archive a course.
     */
    public boolean deleteCourse(String courseCode) {
        String code = courseCode.trim().toUpperCase();
        Optional<Course> opt = courseRepository.findByCode(code);
        if (opt.isPresent()) {
            courseRepository.delete(opt.get());
            keyValueCacheService.evict("cache:courses:all");
            return true;
        }
        return false;
    }

    /**
     * University-wide Attendance Cutoff Compliance Report.
     * Flags all students whose attendance is below minPercentage (default 75%)
     * prior to semester examination hall ticket issuance.
     */
    public Map<String, Object> getAttendanceCutoffReport(double minPercentage) {
        List<Enrollment> allEnrollments = enrollmentRepository.findAll();
        List<Map<String, Object>> flaggedStudents = new ArrayList<>();
        int eligibleCount = 0;
        int debarredCount = 0;

        for (Enrollment e : allEnrollments) {
            boolean isDebarred = e.getAttendancePercentage() < minPercentage;
            if (isDebarred) {
                debarredCount++;
            } else {
                eligibleCount++;
            }

            Map<String, Object> record = new HashMap<>();
            record.put("enrollmentId", e.getId());
            record.put("studentId", e.getStudentId());
            record.put("courseCode", e.getCourseCode());
            record.put("courseTitle", e.getCourseTitle());
            record.put("attendancePercentage", e.getAttendancePercentage());
            record.put("classesAttended", e.getClassesAttended());
            record.put("totalClasses", e.getTotalClasses());
            record.put("status", isDebarred ? "DEBARRED" : "ELIGIBLE");

            // Student lookup
            studentRepository.findById(e.getStudentId()).ifPresent(s -> {
                record.put("studentName", s.getName());
                record.put("rollNumber", s.getRollNumber());
                record.put("department", s.getDepartment());
                record.put("semester", s.getSemester());
            });

            if (isDebarred) {
                flaggedStudents.add(record);
            }
        }

        Map<String, Object> report = new HashMap<>();
        report.put("cutoffPercentage", minPercentage);
        report.put("totalEnrollmentsEvaluated", allEnrollments.size());
        report.put("totalEligible", eligibleCount);
        report.put("totalDebarred", debarredCount);
        report.put("complianceRate", allEnrollments.isEmpty() ? 100.0 : Math.round((eligibleCount * 100.0 / allEnrollments.size()) * 10.0) / 10.0);
        report.put("flaggedRecords", flaggedStudents);

        return report;
    }

    /**
     * University System Overview for Academic Leadership and Registrars.
     */
    public Map<String, Object> getUniversityOverview() {
        long totalStudents = studentRepository.count();
        long totalCourses = courseRepository.count();
        long totalEnrollments = enrollmentRepository.count();

        List<Enrollment> enrollments = enrollmentRepository.findAll();
        double avgAttendance = enrollments.isEmpty() ? 100.0 :
                enrollments.stream().mapToDouble(Enrollment::getAttendancePercentage).average().orElse(100.0);

        long lowAttendanceCount = enrollments.stream().filter(e -> e.getAttendancePercentage() < 75.0).count();

        Map<String, Object> overview = new HashMap<>();
        overview.put("totalStudents", totalStudents);
        overview.put("totalCourses", totalCourses);
        overview.put("totalEnrollments", totalEnrollments);
        overview.put("averageAttendancePercentage", Math.round(avgAttendance * 10.0) / 10.0);
        overview.put("studentsBelow75Percent", lowAttendanceCount);
        overview.put("timestamp", System.currentTimeMillis());

        return overview;
    }

    /**
     * Publish official university academic notice with automatic Base62 Short URL generation.
     */
    public AcademicNotice publishOfficialNotice(String title, String content, String author, String externalUrl) {
        String noticeId = snowflakeIdGenerator.nextIdStr();
        String shortUrl = null;

        if (externalUrl != null && !externalUrl.isBlank()) {
            shortUrl = urlShortenerService.shorten(externalUrl.trim());
        }

        AcademicNotice notice = new AcademicNotice(
                noticeId,
                title.trim(),
                content.trim(),
                "ACADEMIC_OFFICIAL",
                author != null ? author.trim() : "Office of the Registrar",
                System.currentTimeMillis(),
                shortUrl
        );

        AcademicNotice saved = noticeRepository.save(notice);
        keyValueCacheService.evict("cache:notices:all");
        return saved;
    }
}
