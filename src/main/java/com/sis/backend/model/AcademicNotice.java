package com.sis.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;

@Document(collection = "academic_notices")
public class AcademicNotice implements Serializable {

    @Id
    private String id; // Snowflake ID

    private String title;
    private String category; // "INTERNSHIP", "EXAM", "SCHEDULE", "ATTENDANCE", "SCHOLARSHIP", "GENERAL"
    private String content;
    private String sourceUrl; // Original application link
    private String publishedDate;
    private String shortUrl; // Base62 shortened URL
    private long scrapedAt;

    // Engineering Internship attributes
    private String department; // "CSE", "ECE", "EEE", "MECH", "ALL"
    private String company; // "Google", "Intel", "Tesla", "Qualcomm", etc.
    private String deadline; // Upcoming application deadline e.g. "2026-11-30"
    private String stipend; // e.g. "₹85,000 / month" or "$45 / hr"
    private String location; // e.g. "Remote / Bangalore"
    private String terms; // e.g. "Summer 2027", "Winter 2026"
    private boolean active = true;

    public AcademicNotice() {
    }

    public AcademicNotice(String id, String title, String category, String content,
                          String sourceUrl, String publishedDate, String shortUrl, long scrapedAt) {
        this.id = id;
        this.title = title;
        this.category = category;
        this.content = content;
        this.sourceUrl = sourceUrl;
        this.publishedDate = publishedDate;
        this.shortUrl = shortUrl;
        this.scrapedAt = scrapedAt;
        this.department = "ALL";
        this.active = true;
    }

    public AcademicNotice(String id, String title, String category, String content,
                          String sourceUrl, String publishedDate, String shortUrl, long scrapedAt,
                          String department, String company, String deadline, String stipend,
                          String location, String terms) {
        this.id = id;
        this.title = title;
        this.category = category;
        this.content = content;
        this.sourceUrl = sourceUrl;
        this.publishedDate = publishedDate;
        this.shortUrl = shortUrl;
        this.scrapedAt = scrapedAt;
        this.department = department != null ? department : "ALL";
        this.company = company;
        this.deadline = deadline;
        this.stipend = stipend;
        this.location = location;
        this.terms = terms;
        this.active = !isExpired();
    }

    public AcademicNotice(String id, String title, String content, String category,
                          String sourceUrl, long scrapedAt, String shortUrl) {
        this(id, title, category, content, sourceUrl, java.time.LocalDate.now().toString(), shortUrl, scrapedAt);
    }

    /**
     * Check if the internship deadline has passed.
     * Expired internships are never shown to students.
     */
    public boolean isExpired() {
        if (deadline == null || deadline.isBlank()) return false;
        try {
            return java.time.LocalDate.parse(deadline).isBefore(java.time.LocalDate.now());
        } catch (Exception e) {
            return false;
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getPublishedDate() {
        return publishedDate;
    }

    public void setPublishedDate(String publishedDate) {
        this.publishedDate = publishedDate;
    }

    public String getShortUrl() {
        return shortUrl;
    }

    public void setShortUrl(String shortUrl) {
        this.shortUrl = shortUrl;
    }

    public long getScrapedAt() {
        return scrapedAt;
    }

    public void setScrapedAt(long scrapedAt) {
        this.scrapedAt = scrapedAt;
    }

    public long getCreatedAt() {
        return scrapedAt;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getDeadline() {
        return deadline;
    }

    public void setDeadline(String deadline) {
        this.deadline = deadline;
    }

    public String getStipend() {
        return stipend;
    }

    public void setStipend(String stipend) {
        this.stipend = stipend;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getTerms() {
        return terms;
    }

    public void setTerms(String terms) {
        this.terms = terms;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
