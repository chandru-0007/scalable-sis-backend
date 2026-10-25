package com.sis.backend.controller;

import com.sis.backend.core.url.UrlShortenerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class UrlShortenerController {

    private final UrlShortenerService urlShortenerService;

    public UrlShortenerController(UrlShortenerService urlShortenerService) {
        this.urlShortenerService = urlShortenerService;
    }

    /**
     * Shorten Long URL (Snowflake ID + Base62 + Redis Key-Value Store).
     * POST /api/url/shorten
     */
    @PostMapping("/api/url/shorten")
    public ResponseEntity<Map<String, Object>> shortenUrl(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        if (url == null || url.isBlank()) {
            url = body.get("originalUrl");
        }
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", "url or originalUrl is required"));
        }

        String code = urlShortenerService.shorten(url);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("originalUrl", url);
        response.put("code", code);
        response.put("shortUrl", "/r/" + code);
        return ResponseEntity.ok(response);
    }

    /**
     * Redirect Short URL to Long Destination.
     * GET /r/{code}
     */
    @GetMapping("/r/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        String longUrl = urlShortenerService.resolve(code);
        if (longUrl == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(longUrl)).build();
    }
}
