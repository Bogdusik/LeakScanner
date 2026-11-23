package com.leakscanner.service;

import com.leakscanner.model.Vulnerability;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class NpmAuditService {

    private static final Pattern EXACT_VERSION = Pattern.compile("\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.+-]+)?");

    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    @Value("${leakscanner.api.npm.audit-url:https://registry.npmjs.org/-/npm/v1/security/advisories/bulk}")
    private String npmAuditUrl;

    public List<Vulnerability> scanPackageJson(String packageJsonContent) {
        try {
            JsonNode packageJson = objectMapper.readTree(packageJsonContent);
            Map<String, List<String>> packages = collectPackages(packageJson);
            if (packages.isEmpty()) {
                return List.of();
            }
            return callBulkAdvisoryApi(packages);
        } catch (Exception e) {
            log.error("Error scanning package.json with npm audit", e);
            return List.of();
        }
    }

    private Map<String, List<String>> collectPackages(JsonNode packageJson) {
        Map<String, List<String>> packages = new LinkedHashMap<>();
        for (String section : List.of("dependencies", "devDependencies")) {
            JsonNode deps = packageJson.get(section);
            if (deps == null) {
                continue;
            }
            Iterator<Map.Entry<String, JsonNode>> fields = deps.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String version = entry.getValue().asText().replace("^", "").replace("~", "").trim();
                if (EXACT_VERSION.matcher(version).matches()) {
                    packages.computeIfAbsent(entry.getKey(), name -> new ArrayList<>()).add(version);
                }
            }
        }
        return packages;
    }

    private List<Vulnerability> callBulkAdvisoryApi(Map<String, List<String>> packages) {
        List<Vulnerability> vulnerabilities = new ArrayList<>();

        try {
            String body = webClientBuilder.baseUrl(npmAuditUrl).build()
                    .post()
                    .bodyValue(packages)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(20))
                    .block();

            if (body == null || body.isBlank()) {
                return vulnerabilities;
            }

            JsonNode response = objectMapper.readTree(body);

            Iterator<Map.Entry<String, JsonNode>> byPackage = response.fields();
            while (byPackage.hasNext()) {
                Map.Entry<String, JsonNode> entry = byPackage.next();
                for (JsonNode advisory : entry.getValue()) {
                    vulnerabilities.add(toVulnerability(entry.getKey(), advisory));
                }
            }
        } catch (Exception e) {
            log.warn("Error calling npm advisory API: {}", e.getMessage());
        }

        return vulnerabilities;
    }

    private Vulnerability toVulnerability(String packageName, JsonNode advisory) {
        Vulnerability vuln = new Vulnerability();
        vuln.setTitle(advisory.path("title").asText("Security Vulnerability"));
        vuln.setPackageName(packageName);
        vuln.setSeverity(mapSeverity(advisory.path("severity").asText(null)));
        vuln.setUrl(advisory.path("url").asText(null));

        String vulnerableVersions = advisory.path("vulnerable_versions").asText("");
        vuln.setDescription(vulnerableVersions.isEmpty()
                ? "Known vulnerability in " + packageName
                : "Affects " + packageName + " " + vulnerableVersions);

        return vuln;
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
