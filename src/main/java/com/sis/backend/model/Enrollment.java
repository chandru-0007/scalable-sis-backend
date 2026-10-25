package com.sis.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;

/**
 * Enrollment Domain Entity linking a student to a registered course.
 * Tracks attendance and exam grades.
 */
@Document(collection = "enrollments")
@CompoundIndex(name = "student_course_idx", def = "{'studentId': 1, 'courseCode': 1}", unique = true)
public class Enrollment implements Serializable {

    @Id
    private String id; // Snowflake ID

    @Indexed
    private String studentId;

    @Indexed
    private String courseCode;

    private String courseTitle;
    private String grade; // e.g. "A", "B+", "O", "Pending"
    private double gradePoint; // e.g. 9.0
    private double attendancePercentage; // e.g. 85.0
    private int classesAttended;
    private int totalClasses;
    private String targetShard; // Logical partition
    private long enrolledAt;

    private Double marks; // e.g. 95.0
    private String resultStatus = "PENDING"; // "PENDING", "PUBLISHED"
    private String remarks; // Faculty remarks, e.g., "Passed with Distinction"
    private Long publishedAt; // epoch millis when official results were published

    public Enrollment() {
    }

    public Enrollment(String id, String studentId, String courseCode, String courseTitle,
                      String grade, double gradePoint, double attendancePercentage,
                      int classesAttended, int totalClasses, String targetShard, long enrolledAt) {
        this.id = id;
        this.studentId = studentId;
        this.courseCode = courseCode;
        this.courseTitle = courseTitle;
        this.grade = grade;
        this.gradePoint = gradePoint;
        this.attendancePercentage = attendancePercentage;
        this.classesAttended = classesAttended;
        this.totalClasses = totalClasses;
        this.targetShard = targetShard;
        this.enrolledAt = enrolledAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public void setCourseCode(String courseCode) {
        this.courseCode = courseCode;
    }

    public String getCourseTitle() {
        return courseTitle;
    }

    public void setCourseTitle(String courseTitle) {
        this.courseTitle = courseTitle;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public double getGradePoint() {
        return gradePoint;
    }

    public void setGradePoint(double gradePoint) {
        this.gradePoint = gradePoint;
    }

    public double getAttendancePercentage() {
        return attendancePercentage;
    }

    public void setAttendancePercentage(double attendancePercentage) {
        this.attendancePercentage = attendancePercentage;
    }

    public int getClassesAttended() {
        return classesAttended;
    }

    public void setClassesAttended(int classesAttended) {
        this.classesAttended = classesAttended;
    }

    public int getTotalClasses() {
        return totalClasses;
    }

    public void setTotalClasses(int totalClasses) {
        this.totalClasses = totalClasses;
    }

    public String getTargetShard() {
        return targetShard;
    }

    public void setTargetShard(String targetShard) {
        this.targetShard = targetShard;
    }

    public long getEnrolledAt() {
        return enrolledAt;
    }

    public void setEnrolledAt(long enrolledAt) {
        this.enrolledAt = enrolledAt;
    }

    public Double getMarks() {
        return marks;
    }

    public void setMarks(Double marks) {
        this.marks = marks;
    }

    public String getResultStatus() {
        return resultStatus != null ? resultStatus : "PENDING";
    }

    public void setResultStatus(String resultStatus) {
        this.resultStatus = resultStatus;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public Long getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Long publishedAt) {
        this.publishedAt = publishedAt;
    }
}
