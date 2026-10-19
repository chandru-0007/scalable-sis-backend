package com.sis.backend.core.crawler;

import com.sis.backend.core.id.SnowflakeIdGenerator;
import com.sis.backend.core.kv.KeyValueCacheService;
import com.sis.backend.core.url.UrlShortenerService;
import com.sis.backend.model.AcademicNotice;
import com.sis.backend.repository.NoticeRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Web Crawler for Academic Notifications, Circulars, and Exam Notices.
 * Crawls and caches academic updates, attaching shortened links to each circular.
 */
@Service
public class WebCrawlerService {

    private static final String NOTICE_CACHE_KEY = "cache:academic_notices";

    private final NoticeRepository noticeRepository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final UrlShortenerService urlShortenerService;
    private final KeyValueCacheService keyValueCacheService;

    private final AtomicLong crawlCycles = new AtomicLong(0);

    public WebCrawlerService(NoticeRepository noticeRepository,
                             SnowflakeIdGenerator snowflakeIdGenerator,
                             UrlShortenerService urlShortenerService,
                             KeyValueCacheService keyValueCacheService) {
        this.noticeRepository = noticeRepository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.urlShortenerService = urlShortenerService;
        this.keyValueCacheService = keyValueCacheService;
    }

    @PostConstruct
    public void init() {
        if (noticeRepository.count() == 0) {
            seedSampleNotices();
        }
    }

    /**
     * Crawl / refresh academic notices feed.
     */
    public List<AcademicNotice> crawlNotices() {
        crawlCycles.incrementAndGet();
        List<AcademicNotice> notices = noticeRepository.findAllByOrderByScrapedAtDesc();
        if (notices.isEmpty()) {
            notices = seedSampleNotices();
        }
        return notices;
    }

    private List<AcademicNotice> seedSampleNotices() {
        List<AcademicNotice> list = new ArrayList<>();
        String today = LocalDate.now().toString();

        addNotice(list, "End-Semester Examination Schedule Announced", "Examination",
                "Final examination schedules for Autumn 2026 are released. Practical exams commence from Nov 15.",
                "https://university.ac.in/circulars/autumn-2026-exam-schedule.pdf", today);

        addNotice(list, "Merit-cum-Means National Scholarship Open", "Scholarship",
                "Eligible engineering students with GPA > 8.0 can apply for state scholarship grants before Nov 30.",
                "https://university.ac.in/scholarships/merit-means-2026.html", today);

        addNotice(list, "Mandatory 75% Attendance Requirement Circular", "Academic Policy",
                "Students must maintain at least 75% attendance in all enrolled courses to be eligible for hall tickets.",
                "https://university.ac.in/rules/attendance-policy-oct-2026.pdf", today);

        addNotice(list, "Annual TechFest & Hackathon Registration", "Campus Event",
                "48-hour inter-college hackathon with cash pool of 2,00,000 INR. Registrations close this Friday.",
                "https://university.ac.in/events/hackfest-2026-register.html", today);

        addNotice(list, "Campus Recruitment Drive - Google & Microsoft", "Placement",
                "On-campus recruitment drive for 2027 batch starts next month. Resume submissions open on portal.",
                "https://university.ac.in/placements/schedule-tier1-2026.pdf", today);

        noticeRepository.saveAll(list);
        keyValueCacheService.put(NOTICE_CACHE_KEY, "CACHED_" + list.size(), Duration.ofHours(1));

        return list;
    }

    private void addNotice(List<AcademicNotice> list, String title, String category,
                           String content, String sourceUrl, String date) {
        String id = snowflakeIdGenerator.nextIdStr();
        String shortCode = urlShortenerService.shorten(sourceUrl);
        String shortUrl = "/r/" + shortCode;

        AcademicNotice notice = new AcademicNotice(
                id, title, category, content, sourceUrl, date, shortUrl, System.currentTimeMillis()
        );
        list.add(notice);
    }

    public long getCrawlCyclesCount() {
        return crawlCycles.get();
    }
}
