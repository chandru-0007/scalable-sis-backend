# 🎓 Scalable Student Information System (SIS) — Backend Engine

> A high-performance, distributed prototype of a **Scalable Student Information System** built with **Java 21/24, Spring Boot 3.4.3, MongoDB, and Redis**.

This project solves university-scale challenges (high concurrent traffic, massive student data, flash-enrollment rushes) by incorporating **8 Core Distributed System Design Components**.

---

## 🏗️ System Architecture & Component Mapping

| # | System Design Component | Mapping in SIS Functionality | Implementation Details |
| :---: | :--- | :--- | :--- |
| **1** | **Unique ID Generator** | Generates distributed, time-ordered 64-bit IDs for Students, Enrollments, Notices, and Short URLs. | **Twitter Snowflake Algorithm** (41-bit timestamp, 10-bit datacenter/worker, 12-bit sequence). Generates up to 4,096 unique IDs/ms with zero database collision. |
| **2** | **Consistent Hashing** | Maps student records and workloads onto a circular ring without re-shuffling all data when nodes change. | **360° Ring with 100 Virtual Nodes per Shard** using MD5 hashing. Enables smooth distribution and minimal remapping. |
| **3** | **Database Sharding** | Distributes student data and academic records across multiple logical/physical partitions. | **`MongoShardRouter`** routes data across `sis_shard_0`, `sis_shard_1`, and `sis_shard_2`, with live shard distribution metrics. |
| **4** | **Key-Value Store (Redis)** | Caches frequently accessed data (courses, timetables, notices, student sessions). | **Redis Session & Data Cache** with TTL and live Hit/Miss ratio tracking (< 1ms read latency). |
| **5** | **Rate Limiter** | Protects Authentication against brute-force attacks and Course Enrollment from bot seat-sniping. | **Distributed Token Bucket / Counter Window on Redis**. Triggers `HTTP 429 Too Many Requests`. |
| **6** | **Autocomplete** | Instant course code and title suggestions during search. | **In-Memory Prefix Trie Tree** loaded at startup. Sub-millisecond lookup latency. |
| **7** | **URL Shortener** | Converts long academic notice links into compact redirect URLs. | **Base62 Encoding** on 64-bit Snowflake IDs with Redis key-value resolution and `HTTP 302` redirects (`/r/{code}`). |
| **8** | **Web Crawler** | Aggregates academic notices, circulars, exam schedules, and scholarship alerts. | **Jsoup-powered Web Crawler** with automatic URL shortening and Redis caching. |

---

## 🛠️ Technology Stack

- **Language:** Java 21 / 24
- **Framework:** Spring Boot 3.4.3
- **Database:** MongoDB (Atlas Cloud / Local standalone)
- **In-Memory Cache & Key-Value Store:** Redis (Memurai on Windows or Upstash Redis)
- **Security:** Spring Security with BCrypt Password Hashing
- **Build Tool:** Maven Wrapper (`mvnw` / `mvnw.cmd`)
- **HTTP Client / Parser:** Jsoup

---

## 📁 Project Structure

```
sis-backend/
├── src/main/java/com/sis/backend/
│   ├── SisBackendApplication.java
│   ├── config/
│   │   └── SecurityConfig.java              # BCrypt encoder & security filter
│   ├── controller/
│   │   ├── AuthController.java              # Student Register, Login, Me, Logout
│   │   ├── CourseController.java            # Course Catalog & Trie Autocomplete
│   │   ├── EnrollmentController.java        # Course Enrollment, Timetable, Grades, Attendance
│   │   ├── HealthController.java            # Multi-service health check
│   │   ├── IdGeneratorController.java       # Snowflake ID generation & inspection
│   │   ├── NoticeController.java            # Web crawler academic notices
│   │   ├── SystemMetricsController.java     # Live System Design metrics & elastic scaling
│   │   └── UrlShortenerController.java      # URL shortening & redirection (/r/{code})
│   ├── core/
│   │   ├── autocomplete/
│   │   │   └── TrieAutocompleteService.java # Prefix Trie Tree
│   │   ├── crawler/
│   │   │   └── WebCrawlerService.java       # Academic notices scraper
│   │   ├── hashing/
│   │   │   └── ConsistentHashRing.java      # Virtual node MD5 hash ring
│   │   ├── id/
│   │   │   └── SnowflakeIdGenerator.java    # 64-bit Twitter Snowflake
│   │   ├── kv/
│   │   │   └── KeyValueCacheService.java    # Redis cache with hit/miss tracker
│   │   ├── ratelimit/
│   │   │   └── RateLimiterService.java      # Redis Token Bucket Rate Limiter
│   │   ├── sharding/
│   │   │   └── MongoShardRouter.java        # Shard routing & distribution
│   │   └── url/
│   │       └── UrlShortenerService.java     # Base62 URL shortener
│   ├── model/
│   │   ├── AcademicNotice.java
│   │   ├── Course.java
│   │   ├── Enrollment.java
│   │   ├── LoginRequest.java
│   │   ├── RegisterRequest.java
│   │   ├── Student.java
│   │   └── TimetableEntry.java
│   ├── repository/
│   │   ├── CourseRepository.java
│   │   ├── EnrollmentRepository.java
│   │   ├── NoticeRepository.java
│   │   └── StudentRepository.java
│   └── service/
│       ├── AuthSessionService.java          # Redis session token management
│       ├── CourseService.java               # Course catalog & Trie loading
│       └── EnrollmentService.java           # Enrollment, grades, attendance & timetable
├── src/main/resources/
│   └── application.yml                      # Configuration with environment variable overrides
├── .env.example                             # Template environment file (Safe for GitHub)
├── .gitignore                               # Strictly excludes .env and build files
├── pom.xml                                  # Project dependencies
└── README.md
```

---

## ⚙️ Quick Start & Setup

### 1. Prerequisites
- **JDK 21 or newer** installed (`java -version`)
- **Redis** running on port `6379` (Memurai for Windows or Docker `redis:alpine`)
- **MongoDB** running on port `27017` or a MongoDB Atlas Cluster connection URI

### 2. Environment Configuration
Copy the template environment file:
```bash
cp .env.example .env
```

Edit `.env` to configure your database connection string and ports:
```properties
SERVER_PORT=8085
MONGO_URI=mongodb://localhost:27017/sis_db
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
```

### 3. Build & Run
Run using the Maven Wrapper:

**Windows (PowerShell/CMD):**
```powershell
.\mvnw.cmd spring-boot:run
```

**Linux/macOS:**
```bash
./mvnw spring-boot:run
```

The server starts on **`http://localhost:8085`**.

---

## 📡 REST API Reference

### 1. Health Check
- **`GET /api/health`**
  - Confirms server, MongoDB, and Redis connectivity.
  - Response: `{"server":"UP","mongodb":"UP","redis":"UP - PONG"}`

### 2. Unique ID Generator (Twitter Snowflake)
- **`GET /api/id/generate?count=5`**
  - Generates 64-bit time-ordered unique IDs in ~70 microseconds.
- **`GET /api/id/parse?id=370140469725892608`**
  - Deconstructs an ID into Timestamp, UTC datetime, Datacenter ID, Worker ID, and Sequence number.

### 3. Authentication (Registration & Login)
- **`POST /api/auth/register`**
  - Body:
    ```json
    {
      "name": "Chandru S",
      "email": "chandru@univ.edu",
      "password": "Password@123",
      "rollNumber": "22CS014",
      "department": "CSE",
      "semester": 5
    }
    ```
  - Generates Snowflake ID, BCrypt password hash, assigns shard via Consistent Hashing, and issues Redis session token.
- **`POST /api/auth/login`**
  - Body: `{"email": "chandru@univ.edu", "password": "Password@123"}`
  - Protected by Redis Rate Limiter (Max 5 attempts/minute).
- **`GET /api/auth/me`**
  - Header: `Authorization: Bearer <token>`
  - Retrieves student profile from Redis Key-Value Store in **< 1ms**.
- **`POST /api/auth/logout`**
  - Evicts session token from Redis immediately.

### 4. Course Catalog & Trie Autocomplete
- **`GET /api/courses`**
  - Fetches course catalog (served from Redis cache with automatic DB fallback).
- **`GET /api/search/suggest?query=data`**
  - Sub-millisecond Prefix Trie suggestions for courses matching "data".

### 5. Course Enrollment, Timetable & Academic Records
- **`POST /api/courses/enroll`**
  - Body: `{"studentId": "...", "courseCode": "CS101"}`
  - Checks seat capacity, checks rate limit (10/min), shard-routes enrollment record, and invalidates timetable cache.
- **`GET /api/students/{id}/timetable`**
  - Returns student weekly timetable, cached in Redis Key-Value Store.
- **`GET /api/students/{id}/enrollments`**
  - Returns enrolled courses, grades, and attendance metrics.
- **`PUT /api/enrollments/{id}/grade`**
  - Body: `{"grade": "A+", "gradePoint": 9.5}`
- **`PUT /api/enrollments/{id}/attendance`**
  - Body: `{"attended": 28, "total": 30}`

### 6. URL Shortener
- **`POST /api/url/shorten`**
  - Body: `{"url": "https://university.ac.in/circulars/exam.pdf"}`
  - Response: `{"shortUrl": "/r/rlkDbjIC0U", "code": "rlkDbjIC0U"}`
- **`GET /r/{code}`**
  - Redirects (`HTTP 302`) to the original destination URL.

### 7. Academic Notifications (Web Crawler)
- **`GET /api/notices`**
  - Returns crawled academic circulars, scholarship updates, and exam circulars with short links.
- **`POST /api/notices/crawl`**
  - Manually triggers a web crawling refresh cycle.

### 8. System Metrics & Dynamic Shard Scaling
- **`GET /api/system/metrics`**
  - Live unified metrics across all 8 components:
    - Shard record distribution
    - Redis cache hits, misses, and hit ratio %
    - Rate limiter allowed vs rejected counts
    - Autocomplete indexed terms count
    - URL shortener count
    - Web crawler execution count
- **`GET /api/system/hash-ring?key={anyKey}`**
  - Inspects the Consistent Hash Ring and tests shard resolution.
- **`POST /api/system/shards/add?shard=sis_shard_3`**
  - **Elastic Scaling Demo:** Dynamically adds a new shard with 100 virtual nodes to the live ring.
- **`POST /api/system/shards/remove?shard=sis_shard_2`**
  - **Failover Demo:** Dynamically removes a shard from the ring.

---

## 🔒 Security & Privacy Notice

- **No Secrets in Source Control:** Database credentials and secrets are managed exclusively through `.env` and environment variables.
- **Passwords Hashed:** Passwords are never stored in plain text; Spring Security's `BCryptPasswordEncoder` with salt is enforced.
- **Brute Force Protection:** Distributed Redis Token Bucket limits login and enrollment attempts.
- **Safe Distribution:** `.env` is listed in `.gitignore`; only `.env.example` is committed.

---

## 📜 License
This project is developed for educational and academic mini-project demonstration purposes.
