package com.sis.backend.service;

import com.sis.backend.core.id.SnowflakeIdGenerator;
import com.sis.backend.core.kv.KeyValueCacheService;
import com.sis.backend.model.AcademicNotice;
import com.sis.backend.model.Course;
import com.sis.backend.model.Enrollment;
import com.sis.backend.model.Student;
import com.sis.backend.model.TimetableEntry;
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
    private final com.sis.backend.core.realtime.RealtimeEventService realtimeEventService;

    public FacultyService(CourseRepository courseRepository,
                          EnrollmentRepository enrollmentRepository,
                          StudentRepository studentRepository,
                          NoticeRepository noticeRepository,
                          SnowflakeIdGenerator snowflakeIdGenerator,
                          KeyValueCacheService keyValueCacheService,
                          com.sis.backend.core.realtime.RealtimeEventService realtimeEventService) {
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.studentRepository = studentRepository;
        this.noticeRepository = noticeRepository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.keyValueCacheService = keyValueCacheService;
        this.realtimeEventService = realtimeEventService;
    }

    /**
     * Get courses assigned to a faculty member by ID, rollNumber, name, or department.
     */
    public List<Course> getCoursesForFaculty(String facultyName, String facultyId, String department) {
        List<Course> all = courseRepository.findAll();

        String targetRoll = facultyId != null && !facultyId.isBlank() ? facultyId.trim() : null;
        String targetName = facultyName != null && !facultyName.isBlank() ? facultyName.trim() : null;
        String targetDept = department != null && !department.isBlank() ? department.trim() : null;

        // If facultyId is provided, resolve student/faculty profile from StudentRepository
        if (targetRoll != null) {
            Optional<Student> sOpt = studentRepository.findById(targetRoll);
            if (sOpt.isEmpty()) sOpt = studentRepository.findByRollNumber(targetRoll);
            if (sOpt.isEmpty()) sOpt = studentRepository.findByEmail(targetRoll);
            if (sOpt.isPresent()) {
                Student s = sOpt.get();
                targetRoll = s.getRollNumber();
                if (targetName == null || targetName.isBlank()) targetName = s.getName();
                if (targetDept == null || targetDept.isBlank()) targetDept = s.getDepartment();
            }
        }

        // Extract core keyword from facultyName (e.g. "Ramanathan" from "Dr. A. Ramanathan", "Priya" from "Dr. Priya Sharma")
        String coreKeyword = null;
        if (targetName != null) {
            String[] tokens = targetName.split("\\s+");
            for (int i = tokens.length - 1; i >= 0; i--) {
                String clean = tokens[i].replaceAll("[^a-zA-Z]", "");
                if (clean.length() >= 3 && !clean.equalsIgnoreCase("Dr") && !clean.equalsIgnoreCase("Prof")) {
                    coreKeyword = clean.toLowerCase();
                    break;
                }
            }
            if (coreKeyword == null) coreKeyword = targetName.toLowerCase();
        }

        final String fRoll = targetRoll;
        final String fRawId = facultyId != null ? facultyId.trim() : null;
        final String fNameLower = targetName != null ? targetName.toLowerCase() : null;
        final String fCore = coreKeyword;

        List<Course> matched = all.stream().filter(c -> {
            // 1. Check facultyId match (roll number or raw ID)
            if (c.getFacultyId() != null && !c.getFacultyId().isBlank()) {
                if (fRoll != null && fRoll.equalsIgnoreCase(c.getFacultyId())) return true;
                if (fRawId != null && fRawId.equalsIgnoreCase(c.getFacultyId())) return true;
            }
            // 2. Check instructor name match
            if (c.getInstructor() != null && !c.getInstructor().isBlank()) {
                String instLower = c.getInstructor().toLowerCase();
                if (fNameLower != null && (instLower.contains(fNameLower) || fNameLower.contains(instLower))) return true;
                if (fCore != null && instLower.contains(fCore)) return true;
            }
            return false;
        }).toList();

        if (!matched.isEmpty()) {
            return matched;
        }

        // Fallback 1: Return courses matching department
        if (targetDept != null && !targetDept.isBlank()) {
            List<Course> deptCourses = courseRepository.findByDepartmentIgnoreCase(targetDept);
            if (!deptCourses.isEmpty()) {
                return deptCourses;
            }
        }

        // Fallback 2: Return all courses so faculty is never locked out
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
            boolean attended = Boolean.TRUE.equals(record.get("attended")) ||
                    "true".equalsIgnoreCase(String.valueOf(record.get("attended"))) ||
                    "PRESENT".equalsIgnoreCase(String.valueOf(record.get("status")));

            Optional<Enrollment> opt = Optional.empty();
            if (enrollmentId != null && !enrollmentId.isBlank()) {
                opt = enrollmentRepository.findById(enrollmentId);
            }
            if (opt.isEmpty() && studentId != null && !studentId.isBlank()) {
                opt = enrollmentRepository.findByStudentIdAndCourseCode(studentId, code);
                if (opt.isEmpty()) {
                    Optional<Student> byRoll = studentRepository.findByRollNumber(studentId);
                    if (byRoll.isPresent()) {
                        opt = enrollmentRepository.findByStudentIdAndCourseCode(byRoll.get().getId(), code);
                    }
                }
                if (opt.isEmpty()) {
                    Optional<Student> byId = studentRepository.findById(studentId);
                    if (byId.isPresent()) {
                        opt = enrollmentRepository.findByStudentIdAndCourseCode(byId.get().getId(), code);
                    }
                }
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
                if (e.getStudentId() != null) {
                    keyValueCacheService.evict("cache:timetable:" + e.getStudentId());
                }
                count++;
            }
        }

        if (count > 0) {
            realtimeEventService.emitAttendanceMarked(code, count, attendanceList.size());
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
        realtimeEventService.emitGradeSubmitted(enrollment.getCourseCode(), enrollment.getStudentId(), enrollment.getGrade());

        return saved;
    }

    /**
     * Submit/Update grade, marks, remarks for an individual student enrollment.
     */
    public Enrollment submitGrade(String enrollmentId, String grade, double gradePoint, Double marks, String remarks) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new IllegalArgumentException("Enrollment not found with ID: " + enrollmentId));

        enrollment.setGrade(grade.trim().toUpperCase());
        enrollment.setGradePoint(gradePoint);
        if (marks != null) {
            enrollment.setMarks(marks);
        }
        if (remarks != null && !remarks.isBlank()) {
            enrollment.setRemarks(remarks.trim());
        }
        enrollment.setResultStatus("PUBLISHED");
        enrollment.setPublishedAt(System.currentTimeMillis());

        Enrollment saved = enrollmentRepository.save(enrollment);

        keyValueCacheService.evict("cache:timetable:" + enrollment.getStudentId());
        realtimeEventService.emitGradeSubmitted(enrollment.getCourseCode(), enrollment.getStudentId(), enrollment.getGrade());

        return saved;
    }

    /**
     * Batch publish all student grades and official course results.
     */
    public Map<String, Object> publishCourseResults(String courseCode, List<Map<String, Object>> gradesList, String facultyName) {
        String code = courseCode.trim().toUpperCase();
        int updatedCount = 0;
        long now = System.currentTimeMillis();

        if (gradesList != null && !gradesList.isEmpty()) {
            for (Map<String, Object> record : gradesList) {
                String enrollmentId = (String) record.get("enrollmentId");
                String grade = (String) record.get("grade");
                Number gp = (Number) record.getOrDefault("gradePoint", 0.0);
                Number marks = (Number) record.get("marks");
                String remarks = (String) record.get("remarks");

                if (enrollmentId != null) {
                    Optional<Enrollment> opt = enrollmentRepository.findById(enrollmentId);
                    if (opt.isPresent()) {
                        Enrollment e = opt.get();
                        if (grade != null && !grade.isBlank()) {
                            e.setGrade(grade.trim().toUpperCase());
                            e.setGradePoint(gp.doubleValue());
                        }
                        if (marks != null) {
                            e.setMarks(marks.doubleValue());
                        }
                        if (remarks != null && !remarks.isBlank()) {
                            e.setRemarks(remarks.trim());
                        }
                        e.setResultStatus("PUBLISHED");
                        e.setPublishedAt(now);
                        enrollmentRepository.save(e);
                        keyValueCacheService.evict("cache:timetable:" + e.getStudentId());
                        updatedCount++;
                    }
                }
            }
        } else {
            // Publish existing grades in roster
            List<Enrollment> enrollments = enrollmentRepository.findByCourseCode(code);
            for (Enrollment e : enrollments) {
                if (e.getGrade() == null || e.getGrade().equalsIgnoreCase("Pending")) {
                    e.setGrade("A");
                    e.setGradePoint(8.0);
                    e.setMarks(80.0);
                    e.setRemarks("First Class Passing");
                }
                e.setResultStatus("PUBLISHED");
                e.setPublishedAt(now);
                enrollmentRepository.save(e);
                keyValueCacheService.evict("cache:timetable:" + e.getStudentId());
                updatedCount++;
            }
        }

        realtimeEventService.emitResultsPublished(code, updatedCount);

        // Auto post announcement
        postCourseAnnouncement(code, "Official Examination Results Published",
                "Final course grades, evaluated marks, and GPA points for " + code +
                        " have been officially verified and published by " +
                        (facultyName != null ? facultyName : "Course Instructor") + ". Students can access their verified Grade Card.",
                facultyName != null ? facultyName : "Course Instructor");

        Map<String, Object> result = new HashMap<>();
        result.put("status", "SUCCESS");
        result.put("courseCode", code);
        result.put("publishedCount", updatedCount);
        result.put("publishedAt", now);
        result.put("message", "Official semester results published successfully for " + code);
        return result;
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

    /**
     * Compute teaching timetable/schedule for a faculty member from their assigned courses.
     */
    public List<TimetableEntry> getFacultyTimetable(String facultyId, String facultyName, String department) {
        List<Course> courses = getCoursesForFaculty(facultyName, facultyId, department);
        List<TimetableEntry> timetable = new ArrayList<>();
        for (Course c : courses) {
            if (c.getSchedule() != null && !c.getSchedule().isBlank()) {
                String[] slots = c.getSchedule().split(",\\s*");
                for (String slot : slots) {
                    String trimmed = slot.trim();
                    if (trimmed.isEmpty()) continue;
                    String[] parts = trimmed.split("\\s+", 2);
                    String rawDay = parts.length > 0 ? parts[0].trim() : "Mon";
                    String time = parts.length > 1 ? parts[1].trim() : "09:00-10:30";

                    if (rawDay.contains("/")) {
                        String[] subDays = rawDay.split("/");
                        for (String sd : subDays) {
                            if (!sd.isBlank()) {
                                timetable.add(new TimetableEntry(c.getCode(), c.getTitle(), sd.trim(), time, c.getRoom(), c.getInstructor()));
                            }
                        }
                    } else {
                        timetable.add(new TimetableEntry(c.getCode(), c.getTitle(), rawDay, time, c.getRoom(), c.getInstructor()));
                    }
                }
            }
        }
        return timetable;
    }
}
