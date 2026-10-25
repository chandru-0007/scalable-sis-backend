package com.sis.backend.controller;

import com.sis.backend.core.crawler.WebCrawlerService;
import com.sis.backend.model.AcademicNotice;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notices")
@CrossOrigin(origins = "*")
public class NoticeController {

    private final WebCrawlerService webCrawlerService;

    public NoticeController(WebCrawlerService webCrawlerService) {
        this.webCrawlerService = webCrawlerService;
    }

    /**
     * Get Academic Circulars & Engineering Internships (Web Crawler Feed).
     * GET /api/notices?department=CSE
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getNotices(
            @RequestParam(required = false) String department) {
        List<AcademicNotice> notices = webCrawlerService.crawlNotices(department);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("count", notices.size());
        response.put("notices", notices);
        return ResponseEntity.ok(response);
    }

    /**
     * Get Engineering Internships specifically.
     * GET /api/notices/internships?department=CSE
     */
    @GetMapping("/internships")
    public ResponseEntity<Map<String, Object>> getInternships(
            @RequestParam(required = false) String department) {
        List<AcademicNotice> all = webCrawlerService.crawlNotices(department);
        List<AcademicNotice> internships = all.stream()
                .filter(n -> "INTERNSHIP".equalsIgnoreCase(n.getCategory()))
                .toList();

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("count", internships.size());
        response.put("notices", internships);
        return ResponseEntity.ok(response);
    }

    /**
     * Manually trigger crawler cycle & re-index active opportunities.
     * POST /api/notices/crawl
     */
    @PostMapping("/crawl")
    public ResponseEntity<Map<String, Object>> triggerCrawl(
            @RequestParam(required = false) String department) {
        List<AcademicNotice> refreshed = webCrawlerService.refreshSampleNotices();
        if (department != null && !department.isBlank() && !"ALL".equalsIgnoreCase(department)) {
            refreshed = refreshed.stream()
                    .filter(n -> n.getDepartment() == null ||
                            "ALL".equalsIgnoreCase(n.getDepartment()) ||
                            department.equalsIgnoreCase(n.getDepartment()))
                    .toList();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Web crawler cycle completed successfully. Refreshed active engineering internships and circulars.");
        response.put("scrapedCount", refreshed.size());
        response.put("notices", refreshed);
        return ResponseEntity.ok(response);
    }
}
