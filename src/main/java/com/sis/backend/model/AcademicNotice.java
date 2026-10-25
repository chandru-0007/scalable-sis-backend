package com.sis.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;

@Document(collection = "academic_notices")
public class AcademicNotice implements Serializable {

    @Id
    private String id; // Snowflake ID

    private String title;
    private String category;
    private String content;
    private String sourceUrl;
    private String publishedDate;
    private String shortUrl;
    private long scrapedAt;

    // Engineering Internship and Department specific fields
    private String department; // CSE, ECE, EEE, MECH, ALL
    private String company;
    private String deadline;
    private String location;
    private String stipend;
    private String terms;

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
    }

    public AcademicNotice(String id, String title, String content, String category,
                          String sourceUrl, long scrapedAt, String shortUrl) {
        this(id, title, category, content, sourceUrl, java.time.LocalDate.now().toString(), shortUrl, scrapedAt);
    }

    public AcademicNotice(String id, String title, String category, String content,
                          String sourceUrl, String publishedDate, String shortUrl, long scrapedAt,
                          String department, String company, String deadline,
                          String location, String stipend, String terms) {
        this(id, title, category, content, sourceUrl, publishedDate, shortUrl, scrapedAt);
        this.department = department;
        this.company = company;
        this.deadline = deadline;
        this.location = location;
        this.stipend = stipend;
        this.terms = terms;
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

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getStipend() {
        return stipend;
    }

    public void setStipend(String stipend) {
        this.stipend = stipend;
    }

    public String getTerms() {
        return terms;
    }

    public void setTerms(String terms) {
        this.terms = terms;
    }
}
