package com.leakscanner.controller;

import org.springframework.http.MediaType;
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

    @GetMapping(value = "/llms.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> llmsTxt() {
        String content = """
# LeakScanner API — LLM Reference

Base URL: http://localhost:8080

## Authentication
Tokens are passed as request headers (not stored server-side):
- X-GitHub-Token: GitHub personal access token (optional, required for private repos)
- X-GitLab-Token: GitLab personal access token (optional, required for private repos)
- X-Snyk-Token: Snyk API token (optional, enables enhanced vulnerability scanning)

## Endpoints

### POST /api/v1/scan
Scan a repository for secrets, vulnerabilities, and outdated dependencies.

Request body (JSON):
{
  "owner": "string (required) — repository owner/organisation",
  "name": "string (required) — repository name",
  "platform": "github | gitlab (required)"
}

Query params:
- force=true — bypass 1-hour scan cache

Response (ScanResultDTO):
{
  "repository": { "owner": "...", "name": "...", "platform": "..." },
  "secrets": [{ "type": "...", "file": "...", "line": 0, "severity": "CRITICAL|HIGH|MEDIUM|LOW", "pattern": "***[REDACTED]" }],
  "vulnerabilities": [{ "title": "...", "description": "...", "severity": "...", "packageName": "...", "cve": "...", "url": "..." }],
  "outdatedDependencies": [{ "name": "...", "currentVersion": "...", "latestVersion": "...", "type": "NPM|MAVEN|PYTHON|OTHER" }],
  "securityScore": 0-100,
  "lastScanned": "ISO-8601 datetime",
  "scanStatus": "SUCCESS|FAILED"
}

### POST /api/v1/scan/stream
Same as /api/v1/scan but returns Server-Sent Events (SSE) for real-time progress.
Produces: text/event-stream
Event types: progress | secrets | vulnerabilities | dependencies | complete | error

### GET /api/v1/scan/history
Retrieve previous scan results for a repository.

Query params:
- owner (required)
- name (required)
- platform (required)

Response: array of ScanResultDTO

### GET /actuator/health
Returns service health status.
""";
        return ResponseEntity.ok(content);
    }
}
