package com.sis.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Course domain entity.
 * Supports seat capacity tracking, academic period/semester, schedule information,
 * faculty allocation, and rescheduling audit history.
 */
@Document(collection = "courses")
public class Course implements Serializable {

    @Id
    private String id; // Snowflake ID

    @Indexed(unique = true)
    private String code; // e.g., CS101

    private String title;
    private String department;
    private int credits;
    private String instructor;
    private String facultyId; // e.g. "FAC001" or Snowflake ID of instructor
    private int capacity;
    private int enrolled;
    private String schedule; // e.g. "Mon 09:00-10:30, Wed 09:00-10:30"
    private String room;

    // Academic Period & Semester Information
    private int semester = 1; // e.g., 1 to 8
    private String academicYear = "2026-2027";
    private String term = "Odd Semester (Fall 2026)";
    private String startDate = "2026-08-01";
    private String endDate = "2026-12-15";
    private String status = "ACTIVE"; // ACTIVE, UPCOMING, COMPLETED, CANCELLED

    // Audit and Change Tracking
    private List<String> previousFacultyHistory = new ArrayList<>();
    private List<String> rescheduleHistory = new ArrayList<>();

    public Course() {
    }

    public Course(String id, String code, String title, String department, int credits,
                  String instructor, int capacity, int enrolled, String schedule, String room) {
        this.id = id;
        this.code = code;
        this.title = title;
        this.department = department;
        this.credits = credits;
        this.instructor = instructor;
        this.capacity = capacity;
        this.enrolled = enrolled;
        this.schedule = schedule;
        this.room = room;
    }

    public Course(String id, String code, String title, String department, int credits,
                  String instructor, String facultyId, int capacity, int enrolled,
                  String schedule, String room, int semester, String academicYear,
                  String term, String startDate, String endDate, String status) {
        this.id = id;
        this.code = code;
        this.title = title;
        this.department = department;
        this.credits = credits;
        this.instructor = instructor;
        this.facultyId = facultyId;
        this.capacity = capacity;
        this.enrolled = enrolled;
        this.schedule = schedule;
        this.room = room;
        this.semester = semester;
        this.academicYear = academicYear;
        this.term = term;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public int getCredits() {
        return credits;
    }

    public void setCredits(int credits) {
        this.credits = credits;
    }

    public String getInstructor() {
        return instructor;
    }

    public void setInstructor(String instructor) {
        this.instructor = instructor;
    }

    public String getFacultyId() {
        return facultyId;
    }

    public void setFacultyId(String facultyId) {
        this.facultyId = facultyId;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getEnrolled() {
        return enrolled;
    }

    public void setEnrolled(int enrolled) {
        this.enrolled = enrolled;
    }

    public String getSchedule() {
        return schedule;
    }

    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    public int getSemester() {
        return semester;
    }

    public void setSemester(int semester) {
        this.semester = semester;
    }

    public String getAcademicYear() {
        return academicYear;
    }

    public void setAcademicYear(String academicYear) {
        this.academicYear = academicYear;
    }

    public String getTerm() {
        return term;
    }

    public void setTerm(String term) {
        this.term = term;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<String> getPreviousFacultyHistory() {
        if (previousFacultyHistory == null) {
            previousFacultyHistory = new ArrayList<>();
        }
        return previousFacultyHistory;
    }

    public void setPreviousFacultyHistory(List<String> previousFacultyHistory) {
        this.previousFacultyHistory = previousFacultyHistory;
    }

    public List<String> getRescheduleHistory() {
        if (rescheduleHistory == null) {
            rescheduleHistory = new ArrayList<>();
        }
        return rescheduleHistory;
    }

    public void setRescheduleHistory(List<String> rescheduleHistory) {
        this.rescheduleHistory = rescheduleHistory;
    }
}
