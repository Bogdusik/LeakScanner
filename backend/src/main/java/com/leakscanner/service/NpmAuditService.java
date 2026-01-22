package com.leakscanner.service;

import com.leakscanner.model.Vulnerability;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NpmAuditService {
    
    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;
    
    @Value("${leakscanner.api.npm.audit-url:https://registry.npmjs.org/-/npm/v1/security/audits}")
    private String npmAuditUrl;
    
    public List<Vulnerability> scanPackageJson(String packageJsonContent) {
        List<Vulnerability> vulnerabilities = new ArrayList<>();
        
        try {
            // Parse package.json
            JsonNode packageJson = objectMapper.readTree(packageJsonContent);
            
            // Build lockfile structure for npm audit API
            Map<String, Object> auditRequest = buildAuditRequest(packageJson);
            
            // Call npm audit API
            List<Vulnerability> auditResults = callNpmAuditApi(auditRequest);
            vulnerabilities.addAll(auditResults);
            
            // Also check for known vulnerable packages using advisory database
            List<Vulnerability> advisoryResults = checkAdvisoryDatabase(packageJson);
            vulnerabilities.addAll(advisoryResults);
            
        } catch (Exception e) {
            log.error("Error scanning package.json with npm audit", e);
        }
        
        return vulnerabilities;
    }
    
    private Map<String, Object> buildAuditRequest(JsonNode packageJson) {
        // Build a simplified lockfile structure for npm audit
        // npm audit API expects a lockfile format, but we can use package.json with dependencies
        Map<String, Object> request = new java.util.HashMap<>();
        Map<String, Object> dependencies = new java.util.HashMap<>();
        
        // Extract dependencies
        JsonNode deps = packageJson.get("dependencies");
        if (deps != null) {
            Iterator<Map.Entry<String, JsonNode>> fields = deps.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String packageName = entry.getKey();
                String version = entry.getValue().asText().replace("^", "").replace("~", "");
                
                Map<String, Object> depInfo = new java.util.HashMap<>();
                depInfo.put("version", version);
                depInfo.put("integrity", ""); // Not available from package.json
                dependencies.put(packageName, depInfo);
            }
        }
        
        // Extract devDependencies
        JsonNode devDeps = packageJson.get("devDependencies");
        if (devDeps != null) {
            Iterator<Map.Entry<String, JsonNode>> fields = devDeps.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String packageName = entry.getKey();
                String version = entry.getValue().asText().replace("^", "").replace("~", "");
                
                Map<String, Object> depInfo = new java.util.HashMap<>();
                depInfo.put("version", version);
                depInfo.put("integrity", "");
                dependencies.put(packageName, depInfo);
            }
        }
        
        request.put("name", packageJson.get("name").asText("unknown"));
        request.put("version", packageJson.get("version").asText("1.0.0"));
        request.put("requires", true);
        request.put("dependencies", dependencies);
        
        return request;
    }
    
    private List<Vulnerability> callNpmAuditApi(Map<String, Object> auditRequest) {
        List<Vulnerability> vulnerabilities = new ArrayList<>();
        
        try {
            WebClient webClient = webClientBuilder.baseUrl(npmAuditUrl).build();
            
            // npm audit API expects POST with lockfile structure
            Map<String, Object> response = webClient.post()
                    .bodyValue(auditRequest)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            
            if (response != null && response.containsKey("vulnerabilities")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> vulns = (Map<String, Object>) response.get("vulnerabilities");
                
                for (Map.Entry<String, Object> entry : vulns.entrySet()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> vulnData = (Map<String, Object>) entry.getValue();
                    
                    Vulnerability vuln = new Vulnerability();
                    vuln.setTitle((String) vulnData.getOrDefault("title", "Security Vulnerability"));
                    vuln.setDescription((String) vulnData.getOrDefault("overview", "No description available"));
                    vuln.setPackageName(entry.getKey());
                    
                    // Set severity
                    String severity = (String) vulnData.getOrDefault("severity", "medium");
                    vuln.setSeverity(mapSeverity(severity));
                    
                    // Set CVE if available
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> cves = (List<Map<String, Object>>) vulnData.get("cves");
                    if (cves != null && !cves.isEmpty()) {
                        vuln.setCve((String) cves.get(0).get("cve"));
                    }
                    
                    vulnerabilities.add(vuln);
                }
            }
            
        } catch (Exception e) {
            log.warn("Error calling npm audit API, falling back to advisory check", e);
        }
        
        return vulnerabilities;
    }
    
    private List<Vulnerability> checkAdvisoryDatabase(JsonNode packageJson) {
        List<Vulnerability> vulnerabilities = new ArrayList<>();
        
        try {
            // Check GitHub Advisory Database for known vulnerabilities
            JsonNode deps = packageJson.get("dependencies");
            if (deps != null) {
                Iterator<Map.Entry<String, JsonNode>> fields = deps.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    String packageName = entry.getKey();
                    String version = entry.getValue().asText().replace("^", "").replace("~", "");
                    
                    // Check GitHub Security Advisories
                    List<Vulnerability> packageVulns = checkGitHubAdvisories(packageName, version);
                    vulnerabilities.addAll(packageVulns);
                }
            }
            
        } catch (Exception e) {
            log.error("Error checking advisory database", e);
        }
        
        return vulnerabilities;
    }
    
    private List<Vulnerability> checkGitHubAdvisories(String packageName, String version) {
        List<Vulnerability> vulnerabilities = new ArrayList<>();
        
        try {
            WebClient webClient = webClientBuilder.baseUrl("https://api.github.com").build();
            
            // Search GitHub Security Advisories
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/advisories")
                            .queryParam("package", "npm/" + packageName)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            
            if (response != null && response.containsKey("data")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> advisories = (List<Map<String, Object>>) response.get("data");
                
                for (Map<String, Object> advisory : advisories) {
                    Vulnerability vuln = new Vulnerability();
                    vuln.setTitle((String) advisory.getOrDefault("summary", "Security Advisory"));
                    vuln.setDescription((String) advisory.getOrDefault("description", ""));
                    vuln.setPackageName(packageName);
                    vuln.setCve((String) advisory.getOrDefault("cve_id", ""));
                    
                    // Map severity
                    String severity = (String) advisory.getOrDefault("severity", "medium");
                    vuln.setSeverity(mapSeverity(severity));
                    
                    vulnerabilities.add(vuln);
                }
            }
            
        } catch (Exception e) {
            log.debug("Error checking GitHub advisories for {}: {}", packageName, e.getMessage());
        }
        
        return vulnerabilities;
    }
    
    private Vulnerability.Severity mapSeverity(String severity) {
        if (severity == null) {
            return Vulnerability.Severity.MEDIUM;
        }
        
        return switch (severity.toLowerCase()) {
            case "critical" -> Vulnerability.Severity.CRITICAL;
            case "high" -> Vulnerability.Severity.HIGH;
            case "moderate", "medium" -> Vulnerability.Severity.MEDIUM;
            case "low" -> Vulnerability.Severity.LOW;
            default -> Vulnerability.Severity.MEDIUM;
        };
    }
}
