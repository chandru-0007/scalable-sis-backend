package com.sis.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;

/**
 * Domain entity representing an academic assignment posted by course faculty.
 */
@Document(collection = "assignments")
public class Assignment implements Serializable {

    @Id
    private String id; // Snowflake ID

    @Indexed
    private String courseCode;

    private String courseTitle;

    @Indexed
    private String facultyId;

    private String facultyName;

    private String title;

    private String description;

    private int maxMarks; // e.g., 50 or 100

    private long dueDate; // epoch millis

    private long createdAt; // epoch millis

    private int submissionCount = 0;

    private String status = "ACTIVE"; // "ACTIVE" or "CLOSED"

    public Assignment() {
    }

    public Assignment(String id, String courseCode, String courseTitle, String facultyId,
                      String facultyName, String title, String description, int maxMarks,
                      long dueDate, long createdAt, int submissionCount, String status) {
        this.id = id;
        this.courseCode = courseCode != null ? courseCode.trim().toUpperCase() : null;
        this.courseTitle = courseTitle;
        this.facultyId = facultyId;
        this.facultyName = facultyName;
        this.title = title;
        this.description = description;
        this.maxMarks = maxMarks;
        this.dueDate = dueDate;
        this.createdAt = createdAt;
        this.submissionCount = submissionCount;
        this.status = status != null ? status : "ACTIVE";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getFacultyId() {
        return facultyId;
    }

    public void setFacultyId(String facultyId) {
        this.facultyId = facultyId;
    }

    public String getFacultyName() {
        return facultyName;
    }

    public void setFacultyName(String facultyName) {
        this.facultyName = facultyName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getMaxMarks() {
        return maxMarks;
    }

    public void setMaxMarks(int maxMarks) {
        this.maxMarks = maxMarks;
    }

    public long getDueDate() {
        return dueDate;
    }

    public void setDueDate(long dueDate) {
        this.dueDate = dueDate;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public int getSubmissionCount() {
        return submissionCount;
    }

    public void setSubmissionCount(int submissionCount) {
        this.submissionCount = submissionCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
