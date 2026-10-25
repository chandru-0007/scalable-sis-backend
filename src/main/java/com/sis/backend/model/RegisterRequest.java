package com.sis.backend.model;

public class RegisterRequest {

    private String name;
    private String email;
    private String password;
    private String rollNumber;
    private String department;
    private int semester;
    private String role; // "STUDENT", "FACULTY", "ADMIN", "PARENT"

    public RegisterRequest() {
    }

    public RegisterRequest(String name, String email, String password, String rollNumber,
                           String department, int semester) {
        this(name, email, password, rollNumber, department, semester, "STUDENT");
    }

    public RegisterRequest(String name, String email, String password, String rollNumber,
                           String department, int semester, String role) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.rollNumber = rollNumber;
        this.department = department;
        this.semester = semester;
        this.role = role;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRollNumber() {
        return rollNumber;
    }

    public void setRollNumber(String rollNumber) {
        this.rollNumber = rollNumber;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public int getSemester() {
        return semester;
    }

    public void setSemester(int semester) {
        this.semester = semester;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
