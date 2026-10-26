package com.sis.backend.model;

import java.io.Serializable;

public class TimetableEntry implements Serializable {

    private String courseCode;
    private String courseTitle;
    private String dayOfWeek;
    private String timeSlot;
    private String room;
    private String instructor;

    public TimetableEntry() {
    }

    public TimetableEntry(String courseCode, String courseTitle, String dayOfWeek,
                          String timeSlot, String room, String instructor) {
        this.courseCode = courseCode;
        this.courseTitle = courseTitle;
        this.dayOfWeek = dayOfWeek;
        this.timeSlot = timeSlot;
        this.room = room;
        this.instructor = instructor;
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

    public String getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(String dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public String getTimeSlot() {
        return timeSlot;
    }

    public void setTimeSlot(String timeSlot) {
        this.timeSlot = timeSlot;
    }

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    public String getInstructor() {
        return instructor;
    }

    public void setInstructor(String instructor) {
        this.instructor = instructor;
    }

    public String getDay() {
        return dayOfWeek;
    }

    public void setDay(String day) {
        this.dayOfWeek = day;
    }

    public String getTime() {
        return timeSlot;
    }

    public void setTime(String time) {
        this.timeSlot = time;
    }
}
