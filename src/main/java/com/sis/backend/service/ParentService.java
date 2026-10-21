package com.sis.backend.service;

import com.sis.backend.model.AcademicNotice;
import com.sis.backend.model.Enrollment;
import com.sis.backend.model.Student;
import com.sis.backend.repository.EnrollmentRepository;
import com.sis.backend.repository.NoticeRepository;
import com.sis.backend.repository.StudentRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ParentService {

    private final StudentRepository studentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final NoticeRepository noticeRepository;

    public ParentService(StudentRepository studentRepository,
                         EnrollmentRepository enrollmentRepository,
                         NoticeRepository noticeRepository) {
        this.studentRepository = studentRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.noticeRepository = noticeRepository;
    }

    /**
     * Retrieve complete academic health report of a student for parents/guardians.
     * Evaluates attendance compliance and examination progress.
     */
    public Map<String, Object> getWardAcademicReport(String rollNumber) {
        String cleanRoll = rollNumber.trim().toUpperCase();
        Student student = studentRepository.findByRollNumber(cleanRoll)
                .orElseThrow(() -> new IllegalArgumentException("No student found with Roll Number: " + cleanRoll));

        List<Enrollment> enrollments = enrollmentRepository.findByStudentId(student.getId());

        double totalAttendance = 0.0;
        int totalCourses = enrollments.size();
        boolean hasLowAttendanceWarning = false;
        List<Map<String, Object>> courseDetails = new ArrayList<>();

        for (Enrollment e : enrollments) {
            double pct = e.getAttendancePercentage();
            totalAttendance += pct;
            boolean belowCutoff = pct < 75.0;
            if (belowCutoff) {
                hasLowAttendanceWarning = true;
            }

            Map<String, Object> detail = new HashMap<>();
            detail.put("courseCode", e.getCourseCode());
            detail.put("courseTitle", e.getCourseTitle());
            detail.put("attendancePercentage", pct);
            detail.put("classesAttended", e.getClassesAttended());
            detail.put("totalClasses", e.getTotalClasses());
            detail.put("grade", e.getGrade() != null ? e.getGrade() : "In Progress");
            detail.put("gradePoint", e.getGradePoint());
            detail.put("status", belowCutoff ? "BELOW_75_PERCENT_WARNING" : "GOOD_STANDING");
            courseDetails.add(detail);
        }

        double cumulativeAttendance = totalCourses == 0 ? 100.0 : Math.round((totalAttendance / totalCourses) * 10.0) / 10.0;

        // Fetch recent official notices for parents
        List<AcademicNotice> allNotices = noticeRepository.findAll();
        allNotices.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
        List<AcademicNotice> parentNotices = allNotices.stream().limit(5).toList();

        Map<String, Object> report = new HashMap<>();
        report.put("studentName", student.getName());
        report.put("rollNumber", student.getRollNumber());
        report.put("department", student.getDepartment());
        report.put("semester", student.getSemester());
        report.put("cumulativeAttendancePercentage", cumulativeAttendance);
        report.put("hasLowAttendanceWarning", hasLowAttendanceWarning);
        report.put("minimumRequiredAttendance", 75.0);
        report.put("totalEnrolledCourses", totalCourses);
        report.put("courses", courseDetails);
        report.put("recentNotices", parentNotices);

        return report;
    }
}
