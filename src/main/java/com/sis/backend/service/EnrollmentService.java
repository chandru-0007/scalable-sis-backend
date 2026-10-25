package com.sis.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sis.backend.core.hashing.ConsistentHashRing;
import com.sis.backend.core.id.SnowflakeIdGenerator;
import com.sis.backend.core.kv.KeyValueCacheService;
import com.sis.backend.core.ratelimit.RateLimiterService;
import com.sis.backend.model.Course;
import com.sis.backend.model.Enrollment;
import com.sis.backend.model.Student;
import com.sis.backend.model.TimetableEntry;
import com.sis.backend.repository.CourseRepository;
import com.sis.backend.repository.EnrollmentRepository;
import com.sis.backend.repository.StudentRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class EnrollmentService {

    private static final String TIMETABLE_CACHE_PREFIX = "cache:timetable:";
    private static final Duration TIMETABLE_TTL = Duration.ofMinutes(15);

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final StudentRepository studentRepository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final ConsistentHashRing consistentHashRing;
    private final RateLimiterService rateLimiterService;
    private final KeyValueCacheService keyValueCacheService;
    private final com.sis.backend.core.realtime.RealtimeEventService realtimeEventService;
    private final com.sis.backend.core.sharding.MongoShardRouter mongoShardRouter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EnrollmentService(EnrollmentRepository enrollmentRepository,
                             CourseRepository courseRepository,
                             StudentRepository studentRepository,
                             SnowflakeIdGenerator snowflakeIdGenerator,
                             ConsistentHashRing consistentHashRing,
                             RateLimiterService rateLimiterService,
                             KeyValueCacheService keyValueCacheService,
                             com.sis.backend.core.realtime.RealtimeEventService realtimeEventService,
                             com.sis.backend.core.sharding.MongoShardRouter mongoShardRouter) {
        this.enrollmentRepository = enrollmentRepository;
        this.courseRepository = courseRepository;
        this.studentRepository = studentRepository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.consistentHashRing = consistentHashRing;
        this.rateLimiterService = rateLimiterService;
        this.keyValueCacheService = keyValueCacheService;
        this.realtimeEventService = realtimeEventService;
        this.mongoShardRouter = mongoShardRouter;
    }

    public Optional<Student> resolveStudent(String identifier) {
        if (identifier == null || identifier.isBlank()) return Optional.empty();
        String clean = identifier.trim();
        Optional<Student> opt = studentRepository.findById(clean);
        if (opt.isPresent()) return opt;
        opt = studentRepository.findByRollNumber(clean);
        if (opt.isPresent()) return opt;
        opt = studentRepository.findByEmail(clean.toLowerCase());
        if (opt.isPresent()) return opt;
        if (mongoShardRouter != null) {
            opt = mongoShardRouter.findStudent(clean);
        }
        return opt;
    }

    /**
     * Enroll student into a course with Rate Limiting and Capacity check.
     */
    public Enrollment enroll(String studentId, String courseCode) {
        // 1. Rate limiter check (Anti-bot during enrollment rush: max 10 requests / min)
        boolean allowed = rateLimiterService.tryAcquire("enroll:" + studentId, 10, 60);
        if (!allowed) {
            throw new IllegalStateException("Enrollment rate limit exceeded. Please wait a minute before enrolling again.");
        }

        // 2. Student check
        Optional<Student> studentOpt = resolveStudent(studentId);
        if (studentOpt.isEmpty()) {
            throw new IllegalArgumentException("Student not found with ID / Roll Number: " + studentId);
        }
        Student student = studentOpt.get();
        String canonicalStudentId = student.getId();

        // 3. Course check
        String code = courseCode.trim().toUpperCase();
        Course course = courseRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Course not found with code: " + code));

        // 4. Course lifecycle status check (must be ACTIVE or UPCOMING)
        if (course.getStatus() != null && ("COMPLETED".equalsIgnoreCase(course.getStatus()) || "CANCELLED".equalsIgnoreCase(course.getStatus()))) {
            throw new IllegalStateException("Course " + code + " is " + course.getStatus() + " and not accepting new registrations.");
        }

        // 4b. Semester standing validation check (Prevent enrolling in previous semesters)
        if (student.getSemester() > 0 && course.getSemester() > 0 && course.getSemester() < student.getSemester()) {
            throw new IllegalStateException("Semester Restriction: You are currently enrolled in Semester " +
                    student.getSemester() + ". Course " + code + " belongs to Semester " + course.getSemester() +
                    ". Enrolling in previous semester courses is prohibited.");
        }

        // 5. Seat capacity check
        if (course.getEnrolled() >= course.getCapacity()) {
            throw new IllegalStateException("Course " + code + " has reached maximum capacity (" + course.getCapacity() + " seats).");
        }

        // 6. Duplicate enrollment check
        if (enrollmentRepository.existsByStudentIdAndCourseCode(canonicalStudentId, code) ||
            enrollmentRepository.existsByStudentIdAndCourseCode(studentId, code)) {
            throw new IllegalStateException("Student is already enrolled in " + code);
        }

        // 7. Student Timetable Schedule Collision Check (Prevent double-booking)
        if (course.getSchedule() != null && !course.getSchedule().isBlank()) {
            List<Enrollment> existingEnrollments = getStudentEnrollments(canonicalStudentId);
            for (Enrollment existing : existingEnrollments) {
                Optional<Course> existingCourseOpt = courseRepository.findByCode(existing.getCourseCode());
                if (existingCourseOpt.isPresent()) {
                    Course existingCourse = existingCourseOpt.get();
                    if (isScheduleCollision(course.getSchedule(), existingCourse.getSchedule())) {
                        throw new IllegalStateException("Schedule Collision: Cannot register for " + code + " (" + course.getSchedule() + ") because you have an existing class in " + existingCourse.getCode() + " at " + existingCourse.getSchedule());
                    }
                }
            }
        }

        // 6. Generate 64-bit Snowflake ID & assign Consistent Hash Shard
        String enrollmentId = snowflakeIdGenerator.nextIdStr();
        String targetShard = consistentHashRing.getShard(canonicalStudentId);

        Enrollment enrollment = new Enrollment(
                enrollmentId,
                canonicalStudentId,
                code,
                course.getTitle(),
                "In Progress",
                0.0,
                100.0,
                0,
                0,
                targetShard,
                System.currentTimeMillis()
        );

        Enrollment saved = enrollmentRepository.save(enrollment);

        // 7. Increment course enrollment count
        course.setEnrolled(course.getEnrolled() + 1);
        courseRepository.save(course);

        // 8. Invalidate timetable cache
        keyValueCacheService.evict(TIMETABLE_CACHE_PREFIX + canonicalStudentId);
        keyValueCacheService.evict(TIMETABLE_CACHE_PREFIX + studentId);
        keyValueCacheService.evict("cache:courses:all");

        // 9. Real-Time Broadcast: Broadcast live enrollment & updated seat counter
        int seatsLeft = Math.max(0, course.getCapacity() - course.getEnrolled());
        realtimeEventService.emitEnrollment(canonicalStudentId, code, course.getTitle(), targetShard, seatsLeft);

        return saved;
    }

    /**
     * Unenroll/Drop a course.
     */
    public boolean unenroll(String studentId, String courseCode) {
        Optional<Student> studentOpt = resolveStudent(studentId);
        String canonicalStudentId = studentOpt.map(Student::getId).orElse(studentId);
        String code = courseCode.trim().toUpperCase();

        Optional<Enrollment> opt = enrollmentRepository.findByStudentIdAndCourseCode(canonicalStudentId, code);
        if (opt.isEmpty()) {
            opt = enrollmentRepository.findByStudentIdAndCourseCode(studentId, code);
        }
        if (opt.isPresent()) {
            Enrollment e = opt.get();
            enrollmentRepository.delete(e);
            Optional<Course> cOpt = courseRepository.findByCode(code);
            if (cOpt.isPresent()) {
                Course c = cOpt.get();
                c.setEnrolled(Math.max(0, c.getEnrolled() - 1));
                courseRepository.save(c);
            }
            keyValueCacheService.evict(TIMETABLE_CACHE_PREFIX + canonicalStudentId);
            keyValueCacheService.evict(TIMETABLE_CACHE_PREFIX + studentId);
            keyValueCacheService.evict("cache:courses:all");
            return true;
        }
        return false;
    }

    /**
     * Get student timetable with Redis Key-Value Store caching.
     */
    public List<TimetableEntry> getStudentTimetable(String studentId) {
        Optional<Student> studentOpt = resolveStudent(studentId);
        String canonicalStudentId = studentOpt.map(Student::getId).orElse(studentId);
        String cacheKey = TIMETABLE_CACHE_PREFIX + canonicalStudentId;

        // 1. Check Redis cache first
        String cachedJson = keyValueCacheService.get(cacheKey);
        if (cachedJson != null) {
            try {
                return objectMapper.readValue(cachedJson, new TypeReference<List<TimetableEntry>>() {});
            } catch (Exception ignored) {
            }
        }

        // 2. Cache miss: Compute timetable from enrolled courses
        List<Enrollment> enrollments = getStudentEnrollments(canonicalStudentId);
        List<TimetableEntry> timetable = new ArrayList<>();

        for (Enrollment enrollment : enrollments) {
            Optional<Course> courseOpt = courseRepository.findByCode(enrollment.getCourseCode());
            if (courseOpt.isPresent()) {
                Course c = courseOpt.get();
                // Schedule format: "Mon 09:00-10:30, Wed 09:00-10:30"
                if (c.getSchedule() != null) {
                    String[] slots = c.getSchedule().split(",\\s*");
                    for (String slot : slots) {
                        String[] parts = slot.trim().split("\\s+", 2);
                        String day = parts.length > 0 ? parts[0] : "Mon";
                        String time = parts.length > 1 ? parts[1] : "09:00-10:30";
                        timetable.add(new TimetableEntry(c.getCode(), c.getTitle(), day, time, c.getRoom(), c.getInstructor()));
                    }
                }
            }
        }

        // 3. Cache in Redis
        try {
            String json = objectMapper.writeValueAsString(timetable);
            keyValueCacheService.put(cacheKey, json, TIMETABLE_TTL);
        } catch (Exception ignored) {
        }

        return timetable;
    }

    public List<Enrollment> getStudentEnrollments(String studentId) {
        Optional<Student> studentOpt = resolveStudent(studentId);
        String canonicalStudentId = studentOpt.map(Student::getId).orElse(studentId);
        List<Enrollment> list = enrollmentRepository.findByStudentId(canonicalStudentId);
        if (list.isEmpty() && !canonicalStudentId.equals(studentId)) {
            list = enrollmentRepository.findByStudentId(studentId);
        }
        return list;
    }

    public Enrollment updateGrade(String enrollmentId, String grade, double gradePoint) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new IllegalArgumentException("Enrollment not found: " + enrollmentId));

        enrollment.setGrade(grade);
        enrollment.setGradePoint(gradePoint);
        Enrollment updated = enrollmentRepository.save(enrollment);

        keyValueCacheService.evict(TIMETABLE_CACHE_PREFIX + enrollment.getStudentId());
        return updated;
    }

    public Enrollment updateAttendance(String enrollmentId, int attended, int total) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new IllegalArgumentException("Enrollment not found: " + enrollmentId));

        if (total < 0 || attended < 0 || attended > total) {
            throw new IllegalArgumentException("Invalid attendance values: attended cannot exceed total classes");
        }

        enrollment.setClassesAttended(attended);
        enrollment.setTotalClasses(total);
        double pct = total == 0 ? 100.0 : ((double) attended / total) * 100.0;
        enrollment.setAttendancePercentage(Math.round(pct * 10.0) / 10.0);

        Enrollment updated = enrollmentRepository.save(enrollment);
        keyValueCacheService.evict(TIMETABLE_CACHE_PREFIX + enrollment.getStudentId());
        return updated;
    }

    private boolean isScheduleCollision(String sched1, String sched2) {
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
}
