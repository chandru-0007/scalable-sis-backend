package com.sis.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Student Domain Entity representing a registered student.
 * Sharded across logical partitions based on Consistent Hashing of studentId.
 */
@Document(collection = "students")
public class Student {

    @Id
    private String id; // 64-bit Twitter Snowflake ID string

    @Indexed(unique = true)
    private String rollNumber;

    private String name;

    @Indexed(unique = true)
    private String email;

    private String passwordHash; // BCrypt hash

    private String department;

    private int semester;

    private String role = "STUDENT"; // Default "STUDENT", can be "FACULTY", "ADMIN", "PARENT"

    private String targetShard; // Logical partition assigned via Consistent Hashing

    private String profilePicture; // Profile picture URL or Base64 data URL

    private long createdAt;

    public Student() {
    }

    public Student(String id, String rollNumber, String name, String email, String passwordHash,
                   String department, int semester, String targetShard, long createdAt) {
        this(id, rollNumber, name, email, passwordHash, department, semester, "STUDENT", targetShard, createdAt);
    }

    public Student(String id, String rollNumber, String name, String email, String passwordHash,
                   String department, int semester, String role, String targetShard, long createdAt) {
        this.id = id;
        this.rollNumber = rollNumber;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.department = department;
        this.semester = semester;
        this.role = (role != null && !role.isBlank()) ? role.toUpperCase() : "STUDENT";
        this.targetShard = targetShard;
        this.createdAt = createdAt;
    }

    public String getProfilePicture() {
        return profilePicture;
    }

    public void setProfilePicture(String profilePicture) {
        this.profilePicture = profilePicture;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRollNumber() {
        return rollNumber;
    }

    public void setRollNumber(String rollNumber) {
        this.rollNumber = rollNumber;
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

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
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

    public String getTargetShard() {
        return targetShard;
    }

    public void setTargetShard(String targetShard) {
        this.targetShard = targetShard;
    }

    public String getRole() {
        return role != null ? role : "STUDENT";
    }

    public void setRole(String role) {
        this.role = (role != null && !role.isBlank()) ? role.toUpperCase() : "STUDENT";
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
