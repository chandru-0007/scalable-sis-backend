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
     * Get Academic Circulars & Notifications (Web Crawler Feed).
     * GET /api/notices
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getNotices() {
        List<AcademicNotice> notices = webCrawlerService.crawlNotices();
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("count", notices.size());
        response.put("notices", notices);
        return ResponseEntity.ok(response);
    }

    /**
     * Manually trigger crawler cycle.
     * POST /api/notices/crawl
     */
    @PostMapping("/crawl")
    public ResponseEntity<Map<String, Object>> triggerCrawl() {
        List<AcademicNotice> notices = webCrawlerService.crawlNotices();
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Web crawler cycle completed successfully.");
        response.put("scrapedCount", notices.size());
        return ResponseEntity.ok(response);
    }
}
