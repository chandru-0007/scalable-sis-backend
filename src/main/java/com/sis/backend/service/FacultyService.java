package com.sis.backend.service;

import com.sis.backend.core.id.SnowflakeIdGenerator;
import com.sis.backend.core.kv.KeyValueCacheService;
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
public class FacultyService {

    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StudentRepository studentRepository;
    private final NoticeRepository noticeRepository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final KeyValueCacheService keyValueCacheService;

    public FacultyService(CourseRepository courseRepository,
                          EnrollmentRepository enrollmentRepository,
                          StudentRepository studentRepository,
                          NoticeRepository noticeRepository,
                          SnowflakeIdGenerator snowflakeIdGenerator,
                          KeyValueCacheService keyValueCacheService) {
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.studentRepository = studentRepository;
        this.noticeRepository = noticeRepository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.keyValueCacheService = keyValueCacheService;
    }

    /**
     * Get courses assigned to a faculty member by ID, name, or department.
     */
    public List<Course> getCoursesForFaculty(String facultyName, String facultyId, String department) {
        List<Course> all = courseRepository.findAll();
        if (facultyId != null && !facultyId.isBlank()) {
            String fId = facultyId.trim();
            return all.stream()
                    .filter(c -> fId.equalsIgnoreCase(c.getFacultyId()))
                    .toList();
        }
        if (facultyName != null && !facultyName.isBlank()) {
            String lower = facultyName.trim().toLowerCase();
            return all.stream()
                    .filter(c -> c.getInstructor() != null && c.getInstructor().toLowerCase().contains(lower))
                    .toList();
        }
        if (department != null && !department.isBlank()) {
            return courseRepository.findByDepartmentIgnoreCase(department.trim());
        }
        return all;
    }

    public List<Course> getCoursesForFaculty(String facultyName, String department) {
        return getCoursesForFaculty(facultyName, null, department);
    }

    /**
     * Get complete class roster of enrolled students for a specific course.
     */
    public List<Map<String, Object>> getCourseRoster(String courseCode) {
        String code = courseCode.trim().toUpperCase();
        List<Enrollment> enrollments = enrollmentRepository.findByCourseCode(code);
        List<Map<String, Object>> roster = new ArrayList<>();

        for (Enrollment e : enrollments) {
            Map<String, Object> item = new HashMap<>();
            item.put("enrollmentId", e.getId());
            item.put("studentId", e.getStudentId());
            item.put("courseCode", e.getCourseCode());
            item.put("courseTitle", e.getCourseTitle());
            item.put("classesAttended", e.getClassesAttended());
            item.put("totalClasses", e.getTotalClasses());
            item.put("attendancePercentage", e.getAttendancePercentage());
            item.put("grade", e.getGrade());
            item.put("gradePoint", e.getGradePoint());
            item.put("targetShard", e.getTargetShard());

            // Enrich with student profile
            Optional<Student> studentOpt = studentRepository.findById(e.getStudentId());
            if (studentOpt.isPresent()) {
                Student s = studentOpt.get();
                item.put("studentName", s.getName());
                item.put("rollNumber", s.getRollNumber());
                item.put("email", s.getEmail());
                item.put("department", s.getDepartment());
                item.put("semester", s.getSemester());
            } else {
                item.put("studentName", "Unknown Student");
                item.put("rollNumber", "N/A");
                item.put("email", "N/A");
                item.put("department", "CSE");
                item.put("semester", 0);
            }

            roster.add(item);
        }

        // Sort by Roll Number for easy tabular viewing
        roster.sort(Comparator.comparing(m -> String.valueOf(m.getOrDefault("rollNumber", ""))));
        return roster;
    }

    /**
     * Record daily attendance for a batch of students in a course.
     * Automatically invalidates Redis timetable cache so students see live updates.
     */
    public int markAttendance(String courseCode, List<Map<String, Object>> attendanceList) {
        String code = courseCode.trim().toUpperCase();
        int count = 0;

        for (Map<String, Object> record : attendanceList) {
            String studentId = (String) record.get("studentId");
            String enrollmentId = (String) record.get("enrollmentId");
            boolean attended = Boolean.TRUE.equals(record.get("attended")) || "PRESENT".equalsIgnoreCase(String.valueOf(record.get("status")));

            Optional<Enrollment> opt = Optional.empty();
            if (enrollmentId != null && !enrollmentId.isBlank()) {
                opt = enrollmentRepository.findById(enrollmentId);
            } else if (studentId != null && !studentId.isBlank()) {
                opt = enrollmentRepository.findByStudentIdAndCourseCode(studentId, code);
            }

            if (opt.isPresent()) {
                Enrollment e = opt.get();
                int total = e.getTotalClasses() + 1;
                int attendedCount = e.getClassesAttended() + (attended ? 1 : 0);
                double pct = ((double) attendedCount / total) * 100.0;

                e.setTotalClasses(total);
                e.setClassesAttended(attendedCount);
                e.setAttendancePercentage(Math.round(pct * 10.0) / 10.0);

                enrollmentRepository.save(e);
                keyValueCacheService.evict("cache:timetable:" + e.getStudentId());
                count++;
            }
        }

        return count;
    }

    /**
     * Submit/Update grade for an individual student enrollment.
     */
    public Enrollment submitGrade(String enrollmentId, String grade, double gradePoint) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new IllegalArgumentException("Enrollment not found with ID: " + enrollmentId));

        enrollment.setGrade(grade.trim().toUpperCase());
        enrollment.setGradePoint(gradePoint);
        Enrollment saved = enrollmentRepository.save(enrollment);

        keyValueCacheService.evict("cache:timetable:" + enrollment.getStudentId());
        return saved;
    }

    /**
     * Faculty course announcement creation with 64-bit Snowflake ID.
     */
    public AcademicNotice postCourseAnnouncement(String courseCode, String title, String content, String author) {
        String noticeId = snowflakeIdGenerator.nextIdStr();
        String fullTitle = "[" + courseCode.toUpperCase() + "] " + title;

        AcademicNotice notice = new AcademicNotice(
                noticeId,
                fullTitle,
                content,
                "COURSE_UPDATE",
                author != null ? author : "Course Instructor",
                System.currentTimeMillis(),
                null
        );

        AcademicNotice saved = noticeRepository.save(notice);
        keyValueCacheService.evict("cache:notices:all");
        return saved;
    }
}
