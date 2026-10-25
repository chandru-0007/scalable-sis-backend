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
     * Get Academic Circulars & Real Engineering Internships (Web Crawler Feed).
     * GET /api/notices?department=CSE
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getNotices(
            @RequestParam(required = false) String department) {
        List<AcademicNotice> notices = webCrawlerService.getActiveNotices(department);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("count", notices.size());
        response.put("department", department != null ? department : "ALL");
        response.put("notices", notices);
        return ResponseEntity.ok(response);
    }

    /**
     * Get Upcoming Engineering Internships categorized by department.
     * GET /api/notices/internships?department=CSE
     */
    @GetMapping("/internships")
    public ResponseEntity<Map<String, Object>> getInternships(
            @RequestParam(required = false) String department) {
        List<AcademicNotice> internships = webCrawlerService.getUpcomingInternships(department);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("count", internships.size());
        response.put("department", department != null ? department : "ALL");
        response.put("internships", internships);
        return ResponseEntity.ok(response);
    }

    /**
     * Manually trigger crawler cycle.
     * POST /api/notices/crawl?department=CSE
     */
    @PostMapping("/crawl")
    public ResponseEntity<Map<String, Object>> triggerCrawl(
            @RequestParam(required = false) String department) {
        List<AcademicNotice> notices = webCrawlerService.crawlNotices(department);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Web crawler cycle completed successfully. Fetched real engineering internships.");
        response.put("scrapedCount", notices.size());
        response.put("crawlCycles", webCrawlerService.getCrawlCyclesCount());
        return ResponseEntity.ok(response);
    }
}
