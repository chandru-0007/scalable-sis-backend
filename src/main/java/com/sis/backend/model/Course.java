package com.sis.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;

/**
 * Course domain entity.
 * Supports seat capacity tracking and schedule information for timetable generation.
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
    private int capacity;
    private int enrolled;
    private String schedule; // e.g. "Mon 09:00-10:30, Wed 09:00-10:30"
    private String room;

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
}
