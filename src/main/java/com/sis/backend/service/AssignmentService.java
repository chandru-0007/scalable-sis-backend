package com.sis.backend.service;

import com.sis.backend.core.id.SnowflakeIdGenerator;
import com.sis.backend.core.realtime.RealtimeEventService;
import com.sis.backend.model.Assignment;
import com.sis.backend.model.AssignmentSubmission;
import com.sis.backend.model.Course;
import com.sis.backend.model.Enrollment;
import com.sis.backend.model.Student;
import com.sis.backend.repository.AssignmentRepository;
import com.sis.backend.repository.AssignmentSubmissionRepository;
import com.sis.backend.repository.CourseRepository;
import com.sis.backend.repository.EnrollmentRepository;
import com.sis.backend.repository.StudentRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentSubmissionRepository submissionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final RealtimeEventService realtimeEventService;

    public AssignmentService(AssignmentRepository assignmentRepository,
                             AssignmentSubmissionRepository submissionRepository,
                             EnrollmentRepository enrollmentRepository,
                             StudentRepository studentRepository,
                             CourseRepository courseRepository,
                             SnowflakeIdGenerator snowflakeIdGenerator,
                             RealtimeEventService realtimeEventService) {
        this.assignmentRepository = assignmentRepository;
        this.submissionRepository = submissionRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.studentRepository = studentRepository;
        this.courseRepository = courseRepository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.realtimeEventService = realtimeEventService;
    }

    @PostConstruct
    public void seedInitialAssignments() {
        if (assignmentRepository.count() == 0) {
            long now = System.currentTimeMillis();
            long sevenDays = 7L * 24 * 60 * 60 * 1000L;
            long fourteenDays = 14L * 24 * 60 * 60 * 1000L;

            List<Assignment> seedList = List.of(
                    new Assignment(
                            snowflakeIdGenerator.nextIdStr(),
                            "CS101",
                            "Data Structures & Algorithms",
                            "FAC001",
                            "Dr. A. Ramanathan",
                            "Assignment 1: Distributed Hash Ring Implementation",
                            "Implement a 64-virtual-node consistent hash ring with Murmur3 hashing in Java or Python. Handle node join and node departure with minimum key reshuffling.",
                            50,
                            now + sevenDays,
                            now - 86400000L,
                            0,
                            "ACTIVE"
                    ),
                    new Assignment(
                            snowflakeIdGenerator.nextIdStr(),
                            "CS101",
                            "Data Structures & Algorithms",
                            "FAC001",
                            "Dr. A. Ramanathan",
                            "Assignment 2: Red-Black Tree Balancing & Benchmarks",
                            "Construct a self-balancing Red-Black binary search tree. Benchmark lookup times across 100,000 random keys vs. unbalanced BST.",
                            50,
                            now + fourteenDays,
                            now,
                            0,
                            "ACTIVE"
                    ),
                    new Assignment(
                            snowflakeIdGenerator.nextIdStr(),
                            "CS201",
                            "Operating Systems",
                            "FAC002",
                            "Dr. S. Meenakshi",
                            "Assignment 1: Multithreaded Producer-Consumer with Ring Buffer",
                            "Implement POSIX thread synchronization using mutex locks and condition variables. Measure CPU cache coherence and context switch latency.",
                            100,
                            now + sevenDays,
                            now - 43200000L,
                            0,
                            "ACTIVE"
                    ),
                    new Assignment(
                            snowflakeIdGenerator.nextIdStr(),
                            "CS301",
                            "Cloud Computing & Distributed Systems",
                            "FAC001",
                            "Dr. A. Ramanathan",
                            "Assignment 1: Raft Consensus Election Protocol",
                            "Simulate leader election and heartbeat timers under network split-brain partition scenarios using Go or Java.",
                            100,
                            now + fourteenDays,
                            now,
                            0,
                            "ACTIVE"
                    )
            );

            assignmentRepository.saveAll(seedList);
        }
    }

    /**
     * Faculty creates/posts an assignment for a course.
     */
    public Assignment createAssignment(String courseCode, String title, String description,
                                       int maxMarks, long dueDate, String facultyId, String facultyName) {
        String code = courseCode != null ? courseCode.trim().toUpperCase() : "GENERAL";
        String id = snowflakeIdGenerator.nextIdStr();

        String courseTitle = "Academic Course";
        Optional<Course> courseOpt = courseRepository.findByCode(code);
        if (courseOpt.isPresent()) {
            courseTitle = courseOpt.get().getTitle();
            if (facultyName == null || facultyName.isBlank()) {
                facultyName = courseOpt.get().getInstructor();
            }
        }

        Assignment assignment = new Assignment(
                id,
                code,
                courseTitle,
                facultyId != null ? facultyId : "FAC001",
                facultyName != null ? facultyName : "Course Faculty",
                title.trim(),
                description != null ? description.trim() : "",
                maxMarks > 0 ? maxMarks : 100,
                dueDate > 0 ? dueDate : System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000L),
                System.currentTimeMillis(),
                0,
                "ACTIVE"
        );

        Assignment saved = assignmentRepository.save(assignment);
        realtimeEventService.emitAssignmentCreated(code, title, facultyName);
        return saved;
    }

    /**
     * Get all assignments for a specific course.
     */
    public List<Assignment> getAssignmentsByCourse(String courseCode) {
        String code = courseCode.trim().toUpperCase();
        List<Assignment> list = assignmentRepository.findByCourseCode(code);
        // Refresh live submission counts
        for (Assignment a : list) {
            long count = submissionRepository.countByAssignmentId(a.getId());
            a.setSubmissionCount((int) count);
        }
        return list;
    }

    /**
     * Get all assignments for courses taught by a faculty member.
     */
    public List<Assignment> getAssignmentsForFaculty(String facultyName, String facultyId) {
        List<Assignment> all = assignmentRepository.findAll();
        if (facultyId != null && !facultyId.isBlank()) {
            return all.stream().filter(a -> facultyId.equalsIgnoreCase(a.getFacultyId())).toList();
        }
        if (facultyName != null && !facultyName.isBlank()) {
            String lower = facultyName.trim().toLowerCase();
            return all.stream()
                    .filter(a -> a.getFacultyName() != null && a.getFacultyName().toLowerCase().contains(lower))
                    .toList();
        }
        return all;
    }

    /**
     * Get all assignments for the courses in which a student is enrolled.
     * Enriches each assignment with the student's submission status, score, and faculty feedback.
     */
    public List<Map<String, Object>> getAssignmentsForStudent(String studentIdentifier) {
        Optional<Student> studentOpt = resolveStudent(studentIdentifier);
        if (studentOpt.isEmpty()) {
            return Collections.emptyList();
        }

        Student student = studentOpt.get();
        List<Enrollment> enrollments = enrollmentRepository.findByStudentId(student.getId());
        List<String> enrolledCodes = enrollments.stream().map(Enrollment::getCourseCode).toList();

        List<Assignment> assignments;
        if (enrolledCodes.isEmpty()) {
            assignments = Collections.emptyList();
        } else {
            assignments = assignmentRepository.findByCourseCodeIn(enrolledCodes);
        }

        // Batch fetch all student submissions in a single indexed query (resolves N+1 query latency)
        List<AssignmentSubmission> userSubmissions = submissionRepository.findByStudentId(student.getId());
        Map<String, AssignmentSubmission> subMap = new HashMap<>();
        for (AssignmentSubmission s : userSubmissions) {
            if (s.getAssignmentId() != null) {
                subMap.put(s.getAssignmentId(), s);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Assignment a : assignments) {
            Map<String, Object> map = new HashMap<>();
            map.put("assignmentId", a.getId());
            map.put("courseCode", a.getCourseCode());
            map.put("courseTitle", a.getCourseTitle());
            map.put("facultyName", a.getFacultyName());
            map.put("title", a.getTitle());
            map.put("description", a.getDescription());
            map.put("maxMarks", a.getMaxMarks());
            map.put("dueDate", a.getDueDate());
            map.put("createdAt", a.getCreatedAt());
            map.put("status", a.getStatus());

            // Check submission from pre-fetched in-memory map
            AssignmentSubmission sub = subMap.get(a.getId());
            if (sub != null) {
                map.put("isSubmitted", true);
                map.put("submissionId", sub.getId());
                map.put("submissionStatus", sub.getStatus()); // "SUBMITTED" or "GRADED"
                map.put("submissionContent", sub.getSubmissionContent());
                map.put("attachmentUrl", sub.getAttachmentUrl());
                map.put("submittedAt", sub.getSubmittedAt());
                map.put("marksObtained", sub.getMarksObtained());
                map.put("facultyFeedback", sub.getFacultyFeedback());
                map.put("evaluatedBy", sub.getEvaluatedBy());
                map.put("evaluatedAt", sub.getEvaluatedAt());
            } else {
                map.put("isSubmitted", false);
                map.put("submissionStatus", "NOT_SUBMITTED");
                map.put("submissionContent", null);
                map.put("attachmentUrl", null);
                map.put("marksObtained", null);
                map.put("facultyFeedback", null);
            }

            result.add(map);
        }

        // Sort by dueDate ascending
        result.sort(Comparator.comparing(m -> (Long) m.getOrDefault("dueDate", 0L)));
        return result;
    }

    /**
     * Student answers/submits an assignment.
     */
    public AssignmentSubmission submitAssignment(String assignmentId, String studentIdentifier,
                                                String content, String attachmentUrl) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found with ID: " + assignmentId));

        Student student = resolveStudent(studentIdentifier)
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + studentIdentifier));

        // Verify enrollment in course
        boolean isEnrolled = enrollmentRepository.existsByStudentIdAndCourseCode(student.getId(), assignment.getCourseCode());
        if (!isEnrolled) {
            // Auto-enroll or allow submission for flexibility
        }

        Optional<AssignmentSubmission> existingOpt = submissionRepository.findByAssignmentIdAndStudentId(assignmentId, student.getId());
        AssignmentSubmission submission;

        if (existingOpt.isPresent()) {
            // Update existing submission
            submission = existingOpt.get();
            submission.setSubmissionContent(content);
            submission.setAttachmentUrl(attachmentUrl);
            submission.setSubmittedAt(System.currentTimeMillis());
            submission.setStatus("SUBMITTED"); // Reset to submitted if re-answering
        } else {
            // Create fresh submission
            String id = snowflakeIdGenerator.nextIdStr();
            submission = new AssignmentSubmission(
                    id,
                    assignmentId,
                    student.getId(),
                    student.getRollNumber(),
                    student.getName(),
                    assignment.getCourseCode(),
                    assignment.getCourseTitle(),
                    content,
                    attachmentUrl,
                    System.currentTimeMillis(),
                    "SUBMITTED",
                    null,
                    assignment.getMaxMarks(),
                    null,
                    null,
                    null
            );
            // Increment assignment submission count
            assignment.setSubmissionCount(assignment.getSubmissionCount() + 1);
            assignmentRepository.save(assignment);
        }

        AssignmentSubmission saved = submissionRepository.save(submission);
        realtimeEventService.emitAssignmentSubmitted(assignment.getCourseCode(), student.getName(), assignment.getTitle());
        return saved;
    }

    /**
     * Faculty retrieves all student submissions for a given assignment.
     */
    public List<AssignmentSubmission> getSubmissionsForAssignment(String assignmentId) {
        List<AssignmentSubmission> submissions = submissionRepository.findByAssignmentId(assignmentId);
        // Ensure student name and rollNumber are present
        for (AssignmentSubmission s : submissions) {
            if (s.getStudentName() == null || s.getStudentName().isBlank()) {
                studentRepository.findById(s.getStudentId()).ifPresent(st -> {
                    s.setStudentName(st.getName());
                    s.setStudentRollNumber(st.getRollNumber());
                });
            }
        }
        return submissions;
    }

    /**
     * Faculty evaluates a student's assignment submission, marks scores and gives feedback.
     */
    public AssignmentSubmission evaluateSubmission(String submissionId, Double marksObtained,
                                                    String facultyFeedback, String facultyName) {
        AssignmentSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Submission not found with ID: " + submissionId));

        submission.setMarksObtained(marksObtained);
        submission.setFacultyFeedback(facultyFeedback != null ? facultyFeedback.trim() : "");
        submission.setEvaluatedBy(facultyName != null ? facultyName.trim() : "Course Faculty");
        submission.setEvaluatedAt(System.currentTimeMillis());
        submission.setStatus("GRADED");

        AssignmentSubmission saved = submissionRepository.save(submission);
        realtimeEventService.emitAssignmentEvaluated(
                submission.getCourseCode(),
                submission.getStudentId(),
                marksObtained,
                submission.getMaxMarks() != null ? submission.getMaxMarks() : 100
        );
        return saved;
    }

    /**
     * Delete assignment
     */
    public boolean deleteAssignment(String assignmentId) {
        if (assignmentRepository.existsById(assignmentId)) {
            assignmentRepository.deleteById(assignmentId);
            return true;
        }
        return false;
    }

    private final Map<String, Student> studentFastCache = new java.util.concurrent.ConcurrentHashMap<>();

    private Optional<Student> resolveStudent(String identifier) {
        if (identifier == null || identifier.isBlank()) return Optional.empty();
        String clean = identifier.trim();
        Student cached = studentFastCache.get(clean);
        if (cached != null) return Optional.of(cached);

        Optional<Student> opt = studentRepository.findById(clean);
        if (opt.isEmpty()) opt = studentRepository.findByRollNumber(clean);
        if (opt.isEmpty()) opt = studentRepository.findByEmail(clean.toLowerCase());

        if (opt.isPresent()) {
            Student s = opt.get();
            studentFastCache.put(clean, s);
            if (s.getId() != null) studentFastCache.put(s.getId(), s);
            if (s.getRollNumber() != null) studentFastCache.put(s.getRollNumber(), s);
            if (s.getEmail() != null) studentFastCache.put(s.getEmail().toLowerCase(), s);
        }
        return opt;
    }
}
