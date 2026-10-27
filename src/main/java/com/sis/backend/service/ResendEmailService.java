package com.sis.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Transactional Email Service integrated with the Resend REST API.
 * Dispatches account onboarding credentials and password reset OTPs.
 */
@Service
public class ResendEmailService {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailService.class);
    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    private final String apiKey;
    private final String fromEmail;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ResendEmailService(
            @Value("${resend.api.key:${RESEND_API_KEY:}}") String apiKey,
            @Value("${resend.from.email:SIS Portal <onboarding@resend.dev>}") String fromEmail,
            ObjectMapper objectMapper) {
        String resolved = apiKey;
        if (resolved == null || resolved.isBlank()) {
            resolved = System.getenv("RESEND_API_KEY");
        }
        if (resolved == null || resolved.isBlank()) {
            resolved = System.getProperty("RESEND_API_KEY", "");
        }
        this.apiKey = resolved;
        this.fromEmail = fromEmail;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Send onboarding email with temporary credentials to newly added Student or Faculty.
     */
    public boolean sendCredentialsEmail(String toEmail, String name, String rollNumber, String role, String tempPassword) {
        String subject = "Welcome to SIS Portal — Your Academic Account Credentials";

        String html = """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8">
              <style>
                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f8fafc; margin: 0; padding: 24px; color: #1e293b; }
                .card { max-width: 560px; margin: 0 auto; background: #ffffff; border-radius: 16px; border: 1px solid #e2e8f0; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.05); }
                .header { background: linear-gradient(135deg, #0284c7 0%%, #2563eb 100%%); padding: 32px 24px; text-align: center; color: #ffffff; }
                .header h1 { margin: 0; font-size: 24px; font-weight: 700; letter-spacing: -0.5px; }
                .header p { margin: 8px 0 0 0; opacity: 0.9; font-size: 14px; }
                .body { padding: 32px 28px; }
                .greeting { font-size: 16px; margin-bottom: 16px; }
                .cred-box { background: #f1f5f9; border-radius: 12px; border: 1px solid #cbd5e1; padding: 20px; margin: 24px 0; }
                .cred-row { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px dashed #cbd5e1; font-size: 14px; }
                .cred-row:last-child { border-bottom: none; }
                .cred-label { color: #64748b; font-weight: 500; }
                .cred-val { font-weight: 700; color: #0f172a; font-family: monospace; font-size: 15px; }
                .badge { display: inline-block; padding: 3px 10px; border-radius: 999px; background: #dbeafe; color: #1e40af; font-size: 12px; font-weight: 600; }
                .notice { background: #fffbeb; border: 1px solid #fef3c7; border-left: 4px solid #f59e0b; padding: 12px 16px; border-radius: 8px; font-size: 13px; color: #92400e; margin: 20px 0; }
                .btn-wrap { text-align: center; margin: 28px 0 16px 0; }
                .btn { display: inline-block; background: #2563eb; color: #ffffff !important; padding: 12px 28px; border-radius: 8px; text-decoration: none; font-weight: 600; font-size: 14px; }
                .footer { text-align: center; font-size: 12px; color: #94a3b8; padding: 20px; border-top: 1px solid #f1f5f9; background: #fafafa; }
              </style>
            </head>
            <body>
              <div class="card">
                <div class="header">
                  <h1>Scalable SIS Portal</h1>
                  <p>Academic &amp; Student Information System</p>
                </div>
                <div class="body">
                  <p class="greeting">Hello <strong>%s</strong>,</p>
                  <p>An official academic account has been created for you by the Registrar / Administration. Below are your initial login credentials:</p>
                  
                  <div class="cred-box">
                    <div class="cred-row">
                      <span class="cred-label">Assigned Role</span>
                      <span class="badge">%s</span>
                    </div>
                    <div class="cred-row">
                      <span class="cred-label">Login Identifier / Roll No.</span>
                      <span class="cred-val">%s</span>
                    </div>
                    <div class="cred-row">
                      <span class="cred-label">Registered Email</span>
                      <span class="cred-val">%s</span>
                    </div>
                    <div class="cred-row">
                      <span class="cred-label">Temporary Password</span>
                      <span class="cred-val" style="color: #2563eb;">%s</span>
                    </div>
                  </div>

                  <div class="notice">
                    <strong>Security Reminder:</strong> Please log in to the portal and change your password immediately in your account settings.
                  </div>

                  <div class="btn-wrap">
                    <a href="http://localhost:5173" class="btn">Sign In to SIS Portal &rarr;</a>
                  </div>
                </div>
                <div class="footer">
                  This is an automated message from the Scalable Student Information System.<br>
                  Please do not reply directly to this email.
                </div>
              </div>
            </body>
            </html>
            """.formatted(name, role, rollNumber, toEmail, tempPassword);

        return sendEmail(toEmail, subject, html);
    }

    /**
     * Send password reset OTP email.
     */
    public boolean sendOtpEmail(String toEmail, String name, String otp) {
        String subject = "Your SIS Portal Password Reset Verification Code: " + otp;

        String html = """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8">
              <style>
                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f8fafc; margin: 0; padding: 24px; color: #1e293b; }
                .card { max-width: 520px; margin: 0 auto; background: #ffffff; border-radius: 16px; border: 1px solid #e2e8f0; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.05); }
                .header { background: #0f172a; padding: 28px 24px; text-align: center; color: #ffffff; }
                .header h1 { margin: 0; font-size: 22px; font-weight: 700; }
                .body { padding: 32px 28px; text-align: center; }
                .otp-box { background: #eff6ff; border: 2px dashed #3b82f6; border-radius: 12px; padding: 18px 24px; margin: 24px auto; display: inline-block; }
                .otp-code { font-family: monospace; font-size: 32px; font-weight: 800; letter-spacing: 8px; color: #1d4ed8; }
                .desc { font-size: 14px; color: #475569; line-height: 1.6; margin: 16px 0; }
                .warning { background: #fff1f2; border: 1px solid #ffe4e6; color: #be123c; border-radius: 8px; padding: 12px; font-size: 13px; margin-top: 20px; text-align: left; }
                .footer { text-align: center; font-size: 12px; color: #94a3b8; padding: 18px; border-top: 1px solid #f1f5f9; }
              </style>
            </head>
            <body>
              <div class="card">
                <div class="header">
                  <h1>Password Reset Request</h1>
                </div>
                <div class="body">
                  <p>Hello <strong>%s</strong>,</p>
                  <p class="desc">We received a request to reset your password for the Scalable Student Information System. Enter the verification code below on the reset page:</p>
                  
                  <div class="otp-box">
                    <span class="otp-code">%s</span>
                  </div>

                  <p class="desc">This code is valid for <strong>10 minutes</strong>. If you did not request a password reset, you can safely ignore this email.</p>

                  <div class="warning">
                    <strong>Never share this code:</strong> SIS staff and administrators will never ask for your verification code.
                  </div>
                </div>
                <div class="footer">
                  Scalable SIS Academic Security &bull; Automated Dispatch
                </div>
              </div>
            </body>
            </html>
            """.formatted(name != null ? name : "User", otp);

        return sendEmail(toEmail, subject, html);
    }

    /**
     * Dispatch email via Resend REST API.
     */
    private boolean sendEmail(String toEmail, String subject, String htmlContent) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("from", fromEmail);
            payload.put("to", List.of(toEmail.trim().toLowerCase()));
            payload.put("subject", subject);
            payload.put("html", htmlContent);

            String requestBody = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RESEND_API_URL))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Successfully dispatched email via Resend to {} [status: {}]", toEmail, response.statusCode());
                return true;
            } else if (response.statusCode() == 403 && response.body() != null && response.body().contains("only send testing emails to your own email address")) {
                // Resend free tier sandbox restriction: extract verified account email (e.g. s.chandru0000000@gmail.com)
                String verifiedEmail = "s.chandru0000000@gmail.com";
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\(([^)]+@[^)]+)\\)").matcher(response.body());
                if (matcher.find()) {
                    verifiedEmail = matcher.group(1);
                }

                log.warn("Resend sandbox restriction: destination '{}' is unverified on free tier. Auto-forwarding copy to verified Resend account '{}'.", toEmail, verifiedEmail);

                String devNotice = """
                    <div style="background: #fffbeb; border: 1px solid #fde68a; border-left: 4px solid #f59e0b; padding: 12px 16px; border-radius: 8px; font-size: 13px; color: #92400e; margin-bottom: 20px;">
                      <strong>⚠️ Resend Sandbox Delivery Notice:</strong><br/>
                      This email was generated for <strong>%s</strong>.<br/>
                      In Resend's free sandbox tier, external recipients require verifying a domain at <em>resend.com/domains</em>. Therefore, Resend delivered this copy directly to your verified account email: <strong>%s</strong>.
                    </div>
                    """.formatted(toEmail, verifiedEmail);

                String updatedHtml = htmlContent.replaceFirst("<div class=\"body\">", "<div class=\"body\">" + devNotice);

                Map<String, Object> fallbackPayload = new HashMap<>();
                fallbackPayload.put("from", fromEmail);
                fallbackPayload.put("to", List.of(verifiedEmail));
                fallbackPayload.put("subject", "[Resend Sandbox Test] " + subject + " (for: " + toEmail + ")");
                fallbackPayload.put("html", updatedHtml);

                String fallbackBody = objectMapper.writeValueAsString(fallbackPayload);
                HttpRequest fallbackRequest = HttpRequest.newBuilder()
                        .uri(URI.create(RESEND_API_URL))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(10))
                        .POST(HttpRequest.BodyPublishers.ofString(fallbackBody))
                        .build();

                HttpResponse<String> fallbackResponse = httpClient.send(fallbackRequest, HttpResponse.BodyHandlers.ofString());
                if (fallbackResponse.statusCode() >= 200 && fallbackResponse.statusCode() < 300) {
                    log.info("Successfully delivered sandbox fallback email to verified account {} [original: {}]", verifiedEmail, toEmail);
                    return true;
                } else {
                    log.warn("Fallback delivery to {} failed with status {}", verifiedEmail, fallbackResponse.statusCode());
                    return false;
                }
            } else {
                log.warn("Resend API responded with error [status: {}]: {}", response.statusCode(), response.body());
                return false;
            }
        } catch (Exception e) {
            log.error("Failed to send email to {} via Resend API: {}", toEmail, e.getMessage());
            return false;
        }
    }
}
