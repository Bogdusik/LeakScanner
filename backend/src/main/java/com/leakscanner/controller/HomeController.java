package com.leakscanner.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HomeController {
    
    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> home() {
        return ResponseEntity.ok(Map.of(
            "name", "LeakScanner API",
            "version", "1.0.0",
            "status", "running",
            "endpoints", Map.of(
                "health", "/actuator/health",
                "scan", "POST /api/v1/scan",
                "scanHistory", "GET /api/v1/scan/history"
            ),
            "description", "Security Scanner API for GitHub/GitLab repositories"
        ));
    }
}
