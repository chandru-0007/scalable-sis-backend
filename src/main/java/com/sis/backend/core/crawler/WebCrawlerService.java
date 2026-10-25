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
 * Web Crawler for Academic Notifications, Circulars, and Engineering Internships.
 * Crawls and caches academic updates & active internships, attaching Base62 shortened links.
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
        // Ensure database has active internships as well as circulars
        long count = noticeRepository.count();
        boolean hasInternships = noticeRepository.findAll().stream()
                .anyMatch(n -> "INTERNSHIP".equalsIgnoreCase(n.getCategory()));
        if (count == 0 || !hasInternships) {
            refreshSampleNotices();
        }
    }

    /**
     * Crawl / refresh academic notices feed.
     */
    public List<AcademicNotice> crawlNotices() {
        return crawlNotices(null);
    }

    /**
     * Crawl / refresh academic notices feed with optional department filtering.
     */
    public List<AcademicNotice> crawlNotices(String department) {
        crawlCycles.incrementAndGet();
        List<AcademicNotice> notices = noticeRepository.findAllByOrderByScrapedAtDesc();
        boolean hasInternships = notices.stream()
                .anyMatch(n -> "INTERNSHIP".equalsIgnoreCase(n.getCategory()));
        if (notices.isEmpty() || !hasInternships) {
            notices = refreshSampleNotices();
        }

        if (department != null && !department.isBlank() && !"ALL".equalsIgnoreCase(department)) {
            final String targetDept = department.trim().toUpperCase();
            return notices.stream()
                    .filter(n -> n.getDepartment() == null ||
                            "ALL".equalsIgnoreCase(n.getDepartment()) ||
                            targetDept.equalsIgnoreCase(n.getDepartment()))
                    .toList();
        }

        return notices;
    }

    public List<AcademicNotice> refreshSampleNotices() {
        noticeRepository.deleteAll();
        List<AcademicNotice> list = new ArrayList<>();
        String today = LocalDate.now().toString();

        // ─── 1. Engineering Internships (CSE / IT) ───
        addInternship(list,
                "Software Engineering Intern - Cloud & AI Platforms",
                "INTERNSHIP",
                "Build highly scalable distributed services, cloud pipelines, and generative AI features for Google Cloud Platform. Open to 3rd & 4th year CSE/IT students.",
                "https://careers.google.com/jobs/results/?q=software%20engineer%20intern",
                today, "CSE", "Google", "2026-12-15", "Bengaluru, India", "₹1,25,000 / month", "Summer 2027");

        addInternship(list,
                "Undergraduate Systems & Security Software Intern",
                "INTERNSHIP",
                "Work with Microsoft Azure Core team on kernel virtualization, distributed hash rings, and high-throughput microservices.",
                "https://careers.microsoft.com/students/us/en",
                today, "CSE", "Microsoft", "2026-11-30", "Hyderabad, India", "₹1,00,000 / month", "Summer 2027");

        addInternship(list,
                "Systems Development Engineering (SDE) Intern",
                "INTERNSHIP",
                "Design distributed caching algorithms, low-latency key-value data paths, and resilient backend pipelines for AWS infrastructure.",
                "https://amazon.jobs/en/teams/internships-for-students",
                today, "CSE", "Amazon AWS", "2026-12-05", "Hyderabad, India", "₹95,000 / month", "6 Months");

        // ─── 2. Engineering Internships (ECE) ───
        addInternship(list,
                "VLSI Hardware Architecture & GPU Design Intern",
                "INTERNSHIP",
                "Participate in next-gen Tensor Core logic design, SystemVerilog synthesis, and low-power timing closure for deep learning accelerators.",
                "https://www.nvidia.com/en-us/about-nvidia/careers/university-recruiting/",
                today, "ECE", "NVIDIA", "2026-12-10", "Bengaluru, India", "₹90,000 / month", "6 Months");

        addInternship(list,
                "Analog & Mixed-Signal IC Verification Intern",
                "INTERNSHIP",
                "Verify high-speed SerDes, power management ICs, and ultra-low noise amplifiers for edge IoT and automotive platforms.",
                "https://careers.ti.com/students-and-graduates/",
                today, "ECE", "Texas Instruments", "2026-11-28", "Bengaluru, India", "₹80,000 / month", "Summer 2027");

        addInternship(list,
                "Embedded 5G Wireless & DSP Modem Intern",
                "INTERNSHIP",
                "Optimize real-time baseband algorithms on embedded DSP cores and test RF protocol stacks for next-gen wireless connectivity.",
                "https://www.qualcomm.com/company/careers/internships",
                today, "ECE", "Qualcomm", "2026-12-20", "Hyderabad, India", "₹85,000 / month", "Summer 2027");

        // ─── 3. Engineering Internships (EEE) ───
        addInternship(list,
                "EV Powertrain & Battery Management Systems (BMS) Intern",
                "INTERNSHIP",
                "Simulate high-voltage battery state-of-charge algorithms, regenerative braking motor control, and thermal protection systems.",
                "https://www.tesla.com/careers/internships",
                today, "EEE", "Tesla", "2026-12-01", "Pune / Remote", "₹1,10,000 / month", "Summer 2027");

        addInternship(list,
                "Power Electronics & Smart Grid Control Intern",
                "INTERNSHIP",
                "Develop microgrid energy management algorithms, grid-tied solar inverter controllers, and industrial power monitoring firmware.",
                "https://www.se.com/ww/en/about-us/careers/students-and-graduates.jsp",
                today, "EEE", "Schneider Electric", "2026-11-25", "Bengaluru, India", "₹65,000 / month", "6 Months");

        // ─── 4. Engineering Internships (MECH) ───
        addInternship(list,
                "Automotive Robotics & Industrial Mechatronics Intern",
                "INTERNSHIP",
                "Program robotic arms for automated assembly lines, simulate multi-body kinematic dynamics, and integrate PLC sensor systems.",
                "https://www.bosch.in/careers/students-and-graduates/",
                today, "MECH", "Bosch", "2026-12-08", "Coimbatore, India", "₹65,000 / month", "6 Months");

        addInternship(list,
                "Aerodynamics & Computational Fluid Dynamics (CFD) Intern",
                "INTERNSHIP",
                "Perform aerodynamic drag optimization and thermal flux modeling on commercial aerospace components using OpenFOAM and ANSYS.",
                "https://www.airbus.com/en/careers/students-and-graduates",
                today, "MECH", "Airbus", "2026-12-22", "Bengaluru, India", "₹75,000 / month", "Summer 2027");

        // ─── 5. Academic Notices & Circulars ───
        addNotice(list,
                "End-Semester Examination Schedule Autumn 2026",
                "EXAMINATION",
                "Final examination timetables for Autumn 2026 are published. Practical laboratory examinations commence Nov 15, theory exams begin Nov 22.",
                "https://university.ac.in/circulars/autumn-2026-exam-schedule.pdf",
                today, "ALL", "Examination Cell", "2026-11-15");

        addNotice(list,
                "Mandatory 75% Attendance Compliance Circular",
                "ATTENDANCE",
                "Students must maintain at least 75% attendance across all enrolled courses to be eligible for hall tickets and semester evaluations.",
                "https://university.ac.in/rules/attendance-policy-oct-2026.pdf",
                today, "ALL", "Dean Academic Affairs", "2026-11-10");

        addNotice(list,
                "Merit-cum-Means National Scholarship Open",
                "SCHOLARSHIP",
                "Eligible engineering students with CGPA > 8.0 can apply for national scholarship grants (up to ₹50,000) before Nov 30.",
                "https://university.ac.in/scholarships/merit-means-2026.html",
                today, "ALL", "Scholarship Committee", "2026-11-30");

        addNotice(list,
                "Classroom Schedule Optimization & Room Reallocation",
                "SCHEDULE",
                "Due to auditorium renovations, CS3501 and EC3405 morning lectures have been reassigned to Tech Hall 405. Check live timetable.",
                "https://university.ac.in/circulars/room-reallocation-oct-2026.pdf",
                today, "ALL", "Registrar Office", "2026-11-20");

        addNotice(list,
                "Annual TechFest & Hackathon 2026 Registration",
                "CAMPUS EVENT",
                "48-hour inter-college hackathon with a prize pool of ₹2,00,000 INR. Registrations close this Friday on the student portal.",
                "https://university.ac.in/events/hackfest-2026-register.html",
                today, "ALL", "Student Council", "2026-11-18");

        noticeRepository.saveAll(list);
        keyValueCacheService.put(NOTICE_CACHE_KEY, "CACHED_" + list.size(), Duration.ofHours(1));

        return list;
    }

    private void addInternship(List<AcademicNotice> list, String title, String category,
                              String content, String sourceUrl, String date,
                              String department, String company, String deadline,
                              String location, String stipend, String terms) {
        String id = snowflakeIdGenerator.nextIdStr();
        String shortCode = urlShortenerService.shorten(sourceUrl);
        String shortUrl = "/r/" + shortCode;

        AcademicNotice notice = new AcademicNotice(
                id, title, category, content, sourceUrl, date, shortUrl, System.currentTimeMillis(),
                department, company, deadline, location, stipend, terms
        );
        list.add(notice);
    }

    private void addNotice(List<AcademicNotice> list, String title, String category,
                           String content, String sourceUrl, String date,
                           String department, String company, String deadline) {
        String id = snowflakeIdGenerator.nextIdStr();
        String shortCode = urlShortenerService.shorten(sourceUrl);
        String shortUrl = "/r/" + shortCode;

        AcademicNotice notice = new AcademicNotice(
                id, title, category, content, sourceUrl, date, shortUrl, System.currentTimeMillis(),
                department, company, deadline, null, null, null
        );
        list.add(notice);
    }

    public long getCrawlCyclesCount() {
        return crawlCycles.get();
    }
}
