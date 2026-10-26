package com.sis.backend.controller;

import com.sis.backend.service.SystemBenchmarkService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for Quantitative Performance Evaluation & Scalability Scenarios.
 * Exposes live benchmark test suites and failure simulations directly to the frontend.
 */
@RestController
@RequestMapping("/api/system/benchmark")
@CrossOrigin(origins = "*")
public class SystemBenchmarkController {

    private final SystemBenchmarkService systemBenchmarkService;

    public SystemBenchmarkController(SystemBenchmarkService systemBenchmarkService) {
        this.systemBenchmarkService = systemBenchmarkService;
    }

    /**
     * Run comparative workload benchmark (Low, Medium, or High).
     * POST /api/system/benchmark/run?workload=MEDIUM
     */
    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runBenchmark(
            @RequestParam(defaultValue = "MEDIUM") String workload) {
        Map<String, Object> report = systemBenchmarkService.runBenchmark(workload);
        return ResponseEntity.ok(report);
    }

    /**
     * Simulate Failure and Scalability Scenarios.
     * POST /api/system/benchmark/simulate-failure?scenario=SHARD_FAILOVER
     */
    @PostMapping("/simulate-failure")
    public ResponseEntity<Map<String, Object>> simulateFailure(
            @RequestParam(defaultValue = "SHARD_FAILOVER") String scenario) {
        Map<String, Object> result = systemBenchmarkService.simulateScenario(scenario);
        return ResponseEntity.ok(result);
    }
}
