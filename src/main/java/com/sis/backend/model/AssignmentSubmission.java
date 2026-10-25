package com.sis.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;

/**
 * Domain entity representing a student's answer submission and subsequent faculty evaluation.
 */
@Document(collection = "assignment_submissions")
@CompoundIndex(name = "assign_student_unique_idx", def = "{'assignmentId': 1, 'studentId': 1}", unique = true)
public class AssignmentSubmission implements Serializable {

    @Id
    private String id; // Snowflake ID

    @Indexed
    private String assignmentId;

    @Indexed
    private String studentId;

    private String studentRollNumber;

    private String studentName;

    private String courseCode;

    private String courseTitle;

    private String submissionContent; // Student answer text, solution, explanation, or code

    private String attachmentUrl; // Optional repo link, PDF URL, or Google Drive link

    private long submittedAt; // epoch millis

    private String status = "SUBMITTED"; // "SUBMITTED", "GRADED"

    private Double marksObtained; // Assigned by faculty

    private Integer maxMarks; // Inherited from assignment

    private String facultyFeedback; // Review remarks/feedback

    private String evaluatedBy; // Faculty name

    private Long evaluatedAt; // epoch millis

    public AssignmentSubmission() {
    }

    public AssignmentSubmission(String id, String assignmentId, String studentId,
                                String studentRollNumber, String studentName, String courseCode,
                                String courseTitle, String submissionContent, String attachmentUrl,
                                long submittedAt, String status, Double marksObtained,
                                Integer maxMarks, String facultyFeedback, String evaluatedBy,
                                Long evaluatedAt) {
        this.id = id;
        this.assignmentId = assignmentId;
        this.studentId = studentId;
        this.studentRollNumber = studentRollNumber;
        this.studentName = studentName;
        this.courseCode = courseCode != null ? courseCode.trim().toUpperCase() : null;
        this.courseTitle = courseTitle;
        this.submissionContent = submissionContent;
        this.attachmentUrl = attachmentUrl;
        this.submittedAt = submittedAt;
        this.status = status != null ? status : "SUBMITTED";
        this.marksObtained = marksObtained;
        this.maxMarks = maxMarks;
        this.facultyFeedback = facultyFeedback;
        this.evaluatedBy = evaluatedBy;
        this.evaluatedAt = evaluatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAssignmentId() {
        return assignmentId;
    }

    public void setAssignmentId(String assignmentId) {
        this.assignmentId = assignmentId;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getStudentRollNumber() {
        return studentRollNumber;
    }

    public void setStudentRollNumber(String studentRollNumber) {
        this.studentRollNumber = studentRollNumber;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public void setCourseCode(String courseCode) {
        this.courseCode = courseCode != null ? courseCode.trim().toUpperCase() : null;
    }

    public String getCourseTitle() {
        return courseTitle;
    }

    public void setCourseTitle(String courseTitle) {
        this.courseTitle = courseTitle;
    }

    public String getSubmissionContent() {
        return submissionContent;
    }

    public void setSubmissionContent(String submissionContent) {
        this.submissionContent = submissionContent;
    }

    public String getAttachmentUrl() {
        return attachmentUrl;
    }

    public void setAttachmentUrl(String attachmentUrl) {
        this.attachmentUrl = attachmentUrl;
    }

    public long getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(long submittedAt) {
        this.submittedAt = submittedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Double getMarksObtained() {
        return marksObtained;
    }

    public void setMarksObtained(Double marksObtained) {
        this.marksObtained = marksObtained;
    }

    public Integer getMaxMarks() {
        return maxMarks;
    }

    public void setMaxMarks(Integer maxMarks) {
        this.maxMarks = maxMarks;
    }

    public String getFacultyFeedback() {
        return facultyFeedback;
    }

    public void setFacultyFeedback(String facultyFeedback) {
        this.facultyFeedback = facultyFeedback;
    }

    public String getEvaluatedBy() {
        return evaluatedBy;
    }

    public void setEvaluatedBy(String evaluatedBy) {
        this.evaluatedBy = evaluatedBy;
    }

    public Long getEvaluatedAt() {
        return evaluatedAt;
    }

    public void setEvaluatedAt(Long evaluatedAt) {
        this.evaluatedAt = evaluatedAt;
    }
}
