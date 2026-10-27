package com.sis.backend.controller;

import com.sis.backend.core.hashing.ConsistentHashRing;
import com.sis.backend.core.id.SnowflakeIdGenerator;
import com.sis.backend.core.ratelimit.RateLimiterService;
import com.sis.backend.model.LoginRequest;
import com.sis.backend.model.RegisterRequest;
import com.sis.backend.model.Student;
import com.sis.backend.repository.StudentRepository;
import com.sis.backend.service.AuthSessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final StudentRepository studentRepository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final ConsistentHashRing consistentHashRing;
    private final RateLimiterService rateLimiterService;
    private final AuthSessionService authSessionService;
    private final PasswordEncoder passwordEncoder;
    private final com.sis.backend.core.sharding.MongoShardRouter mongoShardRouter;
    private final com.sis.backend.service.BrevoEmailService brevoEmailService;

    public AuthController(StudentRepository studentRepository,
                          SnowflakeIdGenerator snowflakeIdGenerator,
                          ConsistentHashRing consistentHashRing,
                          RateLimiterService rateLimiterService,
                          AuthSessionService authSessionService,
                          PasswordEncoder passwordEncoder,
                          com.sis.backend.core.sharding.MongoShardRouter mongoShardRouter,
                          com.sis.backend.service.BrevoEmailService brevoEmailService) {
        this.studentRepository = studentRepository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.consistentHashRing = consistentHashRing;
        this.rateLimiterService = rateLimiterService;
        this.authSessionService = authSessionService;
        this.passwordEncoder = passwordEncoder;
        this.mongoShardRouter = mongoShardRouter;
        this.brevoEmailService = brevoEmailService;
    }

    @jakarta.annotation.PostConstruct
    public void seedDemoUsers() {
        if (!studentRepository.existsByEmail("chandru@college.edu") && !studentRepository.existsByRollNumber("22CS014")) {
            String id = snowflakeIdGenerator.nextIdStr();
            studentRepository.save(new Student(
                    id, "22CS014", "Chandru S", "chandru@college.edu",
                    passwordEncoder.encode("Chandru@123"), "CSE", 5, "STUDENT",
                    consistentHashRing.getShard(id), System.currentTimeMillis()
            ));
        }
        if (!studentRepository.existsByEmail("ramanathan@college.edu") && !studentRepository.existsByRollNumber("FAC001")) {
            String id = snowflakeIdGenerator.nextIdStr();
            studentRepository.save(new Student(
                    id, "FAC001", "Dr. A. Ramanathan", "ramanathan@college.edu",
                    passwordEncoder.encode("Faculty@123"), "CSE", 0, "FACULTY",
                    consistentHashRing.getShard(id), System.currentTimeMillis()
            ));
        }
        if (!studentRepository.existsByEmail("admin@college.edu") && !studentRepository.existsByRollNumber("ADM001")) {
            String id = snowflakeIdGenerator.nextIdStr();
            studentRepository.save(new Student(
                    id, "ADM001", "Dr. K. Swaminathan (Registrar)", "admin@college.edu",
                    passwordEncoder.encode("Admin@123"), "ADMIN", 0, "ADMIN",
                    consistentHashRing.getShard(id), System.currentTimeMillis()
            ));
        }
    }

    /**
     * User Registration (Signup)
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest request) {
        Map<String, Object> response = new HashMap<>();

        // 1. Validation
        if (request.getEmail() == null || request.getEmail().isBlank() ||
            request.getPassword() == null || request.getPassword().isBlank() ||
            request.getName() == null || request.getName().isBlank() ||
            request.getRollNumber() == null || request.getRollNumber().isBlank()) {
            response.put("status", "ERROR");
            response.put("message", "name, email, password, and rollNumber are required fields.");
            return ResponseEntity.badRequest().body(response);
        }

        // 2. Uniqueness check
        if (studentRepository.existsByEmail(request.getEmail().trim().toLowerCase())) {
            response.put("status", "ERROR");
            response.put("message", "A user with this email already exists.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        if (studentRepository.existsByRollNumber(request.getRollNumber().trim())) {
            response.put("status", "ERROR");
            response.put("message", "A user with this roll number/ID already exists.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        // 3. System Design Component 1: Unique ID Generator (Snowflake)
        String studentId = snowflakeIdGenerator.nextIdStr();

        // 4. System Design Component 2: Consistent Hashing & Sharding
        String targetShard = consistentHashRing.getShard(studentId);

        // 5. BCrypt Password Hashing
        String passwordHash = passwordEncoder.encode(request.getPassword());

        String role = (request.getRole() != null && !request.getRole().isBlank()) 
                ? request.getRole().trim().toUpperCase() : "STUDENT";

        // 6. Save to Database
        Student student = new Student(
                studentId,
                request.getRollNumber().trim(),
                request.getName().trim(),
                request.getEmail().trim().toLowerCase(),
                passwordHash,
                request.getDepartment() != null ? request.getDepartment().trim() : "CSE",
                request.getSemester() > 0 ? request.getSemester() : 1,
                role,
                targetShard,
                System.currentTimeMillis()
        );

        Student saved = studentRepository.save(student);

        // 7. System Design Component 3: Redis Session Token
        String token = authSessionService.createSession(saved);

        response.put("status", "SUCCESS");
        response.put("message", "User registered successfully");
        response.put("token", token);

        Map<String, Object> profile = new HashMap<>();
        profile.put("studentId", saved.getId());
        profile.put("name", saved.getName());
        profile.put("email", saved.getEmail());
        profile.put("rollNumber", saved.getRollNumber());
        profile.put("department", saved.getDepartment());
        profile.put("semester", saved.getSemester());
        profile.put("role", saved.getRole());
        profile.put("assignedShard", saved.getTargetShard());
        profile.put("profilePicture", saved.getProfilePicture() != null ? saved.getProfilePicture() : "");

        response.put("student", profile);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * User Authentication (Login)
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request) {
        Map<String, Object> response = new HashMap<>();

        if (request.getEmail() == null || request.getPassword() == null) {
            response.put("status", "ERROR");
            response.put("message", "Email and password are required.");
            return ResponseEntity.badRequest().body(response);
        }

        String identifier = request.getEmail().trim();

        // 1. System Design Component 4: Rate Limiter on Redis (Max 5 attempts / 60 seconds)
        boolean allowed = rateLimiterService.tryAcquire("login:" + identifier.toLowerCase(), 5, 60);
        if (!allowed) {
            response.put("status", "RATE_LIMITED");
            response.put("message", "Too many failed/consecutive login attempts. Rate limit exceeded (Max 5/min). Try again later.");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
        }

        // 2. Fetch user from live physical shards or central catalog by email or rollNumber
        Optional<Student> studentOpt = mongoShardRouter.findStudent(identifier);
        if (studentOpt.isEmpty()) {
            studentOpt = studentRepository.findByEmail(identifier.toLowerCase());
            if (studentOpt.isEmpty()) {
                studentOpt = studentRepository.findByRollNumber(identifier);
            }
        }
        if (studentOpt.isEmpty()) {
            response.put("status", "ERROR");
            response.put("message", "Invalid email/ID or password.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        Student student = studentOpt.get();

        // 3. Verify BCrypt password
        if (!passwordEncoder.matches(request.getPassword(), student.getPasswordHash())) {
            response.put("status", "ERROR");
            response.put("message", "Invalid email or password.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        // 4. Create session token in Redis Key-Value Store
        String token = authSessionService.createSession(student);

        response.put("status", "SUCCESS");
        response.put("message", "Login successful");
        response.put("token", token);

        Map<String, Object> profile = new HashMap<>();
        profile.put("studentId", student.getId());
        profile.put("name", student.getName());
        profile.put("email", student.getEmail());
        profile.put("rollNumber", student.getRollNumber());
        profile.put("department", student.getDepartment());
        profile.put("semester", student.getSemester());
        profile.put("role", student.getRole());
        profile.put("assignedShard", student.getTargetShard());
        profile.put("profilePicture", student.getProfilePicture() != null ? student.getProfilePicture() : "");

        response.put("student", profile);

        return ResponseEntity.ok(response);
    }

    /**
     * Get Current Authenticated Student from Redis Session Cache
     * GET /api/auth/me
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentStudent(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "token", required = false) String tokenParam) {

        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7).trim();
        } else if (tokenParam != null && !tokenParam.isBlank()) {
            token = tokenParam.trim();
        }

        Map<String, Object> response = new HashMap<>();
        if (token == null) {
            response.put("status", "ERROR");
            response.put("message", "Authorization token missing.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        Map<String, String> sessionData = authSessionService.getSession(token);
        if (sessionData == null || sessionData.isEmpty()) {
            response.put("status", "ERROR");
            response.put("message", "Session invalid or expired. Please log in again.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        // Live check: reflect any manual changes made in MongoDB Compass directly on physical shards
        String studentId = sessionData.get("studentId");
        String rollNumber = sessionData.get("rollNumber");
        Optional<Student> liveOpt = mongoShardRouter.findStudent(studentId != null ? studentId : rollNumber);
        if (liveOpt.isPresent()) {
            Student live = liveOpt.get();
            sessionData.put("name", live.getName());
            sessionData.put("department", live.getDepartment());
            sessionData.put("semester", String.valueOf(live.getSemester()));
            sessionData.put("role", live.getRole() != null ? live.getRole() : "STUDENT");
            sessionData.put("targetShard", live.getTargetShard());
            sessionData.put("profilePicture", live.getProfilePicture() != null ? live.getProfilePicture() : "");
            authSessionService.updateSession(token, sessionData);
        }

        response.put("status", "SUCCESS");
        response.put("student", sessionData);
        return ResponseEntity.ok(response);
    }

    /**
     * Student Logout (Evict session from Redis)
     * POST /api/auth/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "token", required = false) String tokenParam) {

        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7).trim();
        } else if (tokenParam != null && !tokenParam.isBlank()) {
            token = tokenParam.trim();
        }

        Map<String, Object> response = new HashMap<>();
        if (token != null) {
            authSessionService.deleteSession(token);
        }

        response.put("status", "SUCCESS");
        response.put("message", "Logged out successfully");
        return ResponseEntity.ok(response);
    }

    /**
     * Send OTP for Password Reset via Resend API and Redis (10 min TTL).
     * POST /api/auth/forgot-password/send-otp
     */
    @PostMapping("/forgot-password/send-otp")
    public ResponseEntity<Map<String, Object>> sendForgotPasswordOtp(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", "Email or ID is required."));
        }
        String identifier = email.trim();

        // 1. Fetch user by email or rollNumber
        Optional<Student> studentOpt = mongoShardRouter.findStudent(identifier);
        if (studentOpt.isEmpty()) {
            studentOpt = studentRepository.findByEmail(identifier.toLowerCase());
            if (studentOpt.isEmpty()) {
                studentOpt = studentRepository.findByRollNumber(identifier);
            }
        }
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "ERROR", "message", "No account registered with this email or ID."));
        }

        Student student = studentOpt.get();

        // 2. Rate limit OTP requests (Max 3 requests / 5 minutes)
        boolean allowed = rateLimiterService.tryAcquire("otp:" + student.getEmail().toLowerCase(), 3, 300);
        if (!allowed) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("status", "RATE_LIMITED", "message", "Too many OTP requests. Please wait a few minutes before trying again."));
        }

        // 3. Generate 6-digit OTP
        String otp = String.format("%06d", new java.security.SecureRandom().nextInt(1_000_000));

        // 4. Store in Redis with 10-minute TTL
        String redisKey = "otp:forgot:" + student.getEmail().toLowerCase();
        authSessionService.storeOtp(redisKey, otp, java.time.Duration.ofMinutes(10));

        // 5. Send via Brevo Email Service
        boolean emailSent = brevoEmailService.sendOtpEmail(student.getEmail(), student.getName(), otp);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Verification code sent to " + student.getEmail());
        response.put("email", student.getEmail());
        response.put("emailDispatched", emailSent);
        response.put("demoOtp", otp);
        return ResponseEntity.ok(response);
    }

    /**
     * Reset Password using OTP.
     * POST /api/auth/forgot-password/reset
     */
    @PostMapping("/forgot-password/reset")
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String otp = body.get("otp");
        String newPassword = body.get("newPassword");

        if (email == null || otp == null || newPassword == null || newPassword.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", "Email, OTP, and new password are required."));
        }

        String identifier = email.trim();
        Optional<Student> studentOpt = mongoShardRouter.findStudent(identifier);
        if (studentOpt.isEmpty()) {
            studentOpt = studentRepository.findByEmail(identifier.toLowerCase());
            if (studentOpt.isEmpty()) {
                studentOpt = studentRepository.findByRollNumber(identifier);
            }
        }
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "ERROR", "message", "User not found."));
        }

        Student student = studentOpt.get();
        String redisKey = "otp:forgot:" + student.getEmail().toLowerCase();

        String cachedOtp = authSessionService.getOtp(redisKey);
        if (cachedOtp == null) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", "OTP has expired or was not requested. Please request a new code."));
        }

        if (!cachedOtp.equals(otp.trim())) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", "Invalid verification code. Please check and try again."));
        }

        // Update password hash
        student.setPasswordHash(passwordEncoder.encode(newPassword.trim()));
        studentRepository.save(student);

        // Invalidate OTP in Redis
        authSessionService.deleteOtp(redisKey);

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Password has been reset successfully. Please log in with your new password."
        ));
    }

    /**
     * Change Password when logged in.
     * POST /api/auth/change-password
     */
    @PostMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Session-Token", required = false) String sessionHeader,
            @RequestBody Map<String, String> body) {

        String token = sessionHeader;
        if (token == null && authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7).trim();
        }
        if (token == null || token.isBlank()) {
            token = body.get("token");
        }

        String identifier = body.get("email");
        if (identifier == null || identifier.isBlank()) {
            identifier = body.get("rollNumber");
        }

        Map<String, String> session = token != null ? authSessionService.getSession(token) : null;
        String studentId = session != null ? session.get("studentId") : null;

        Optional<Student> studentOpt = Optional.empty();
        if (studentId != null) {
            studentOpt = studentRepository.findById(studentId);
        }
        if (studentOpt.isEmpty() && identifier != null) {
            studentOpt = studentRepository.findByEmail(identifier.trim().toLowerCase());
            if (studentOpt.isEmpty()) {
                studentOpt = studentRepository.findByRollNumber(identifier.trim());
            }
        }

        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "ERROR", "message", "User session expired or not found. Please log in."));
        }

        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");

        if (oldPassword == null || newPassword == null || newPassword.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", "Current password and new password are required."));
        }

        Student student = studentOpt.get();
        if (!passwordEncoder.matches(oldPassword, student.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "ERROR", "message", "Current password does not match our records."));
        }

        student.setPasswordHash(passwordEncoder.encode(newPassword.trim()));
        studentRepository.save(student);

        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Password changed successfully."));
    }
}
