package com.leakscanner.service;

import com.leakscanner.dto.RepositoryDTO;
import com.leakscanner.model.SecretLeak;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecretScannerService {
    
    private final WebClient.Builder webClientBuilder;
    private final GitHubService githubService;
    private final GitLabService gitLabService;
    private final ExecutorService executorService = Executors.newFixedThreadPool(8);
    
    // Common secret patterns - improved with better regex
    private static final List<SecretPattern> SECRET_PATTERNS = List.of(
        // AWS Access Key ID - must start with AKIA and be exactly 20 chars
        new SecretPattern("AWS_ACCESS_KEY_ID", Pattern.compile("AKIA[0-9A-Z]{16}"), SecretLeak.Severity.CRITICAL),
        // AWS Secret Access Key - base64 encoded, 40 chars
        new SecretPattern("AWS_SECRET_ACCESS_KEY", Pattern.compile("(?i)aws[_-]?secret[_-]?access[_-]?key\\s*[:=]\\s*([A-Za-z0-9/+=]{40})"), SecretLeak.Severity.CRITICAL),
        // GitHub Personal Access Token - ghp_ followed by 36 chars
        new SecretPattern("GitHub Token", Pattern.compile("ghp_[A-Za-z0-9]{36}"), SecretLeak.Severity.HIGH),
        // GitLab Personal Access Token - glpat- followed by 20+ chars
        new SecretPattern("GitLab Token", Pattern.compile("glpat-[A-Za-z0-9_-]{20,}"), SecretLeak.Severity.HIGH),
        // Private Keys - RSA, EC, OpenSSH
        new SecretPattern("Private Key", Pattern.compile("-----BEGIN (RSA |EC |OPENSSH |DSA |PGP )?PRIVATE KEY-----"), SecretLeak.Severity.CRITICAL),
        // Generic API Keys - more specific pattern
        new SecretPattern("API Key", Pattern.compile("(?i)(api[_-]?key|apikey)\\s*[:=]\\s*['\"]?([A-Za-z0-9_-]{32,})['\"]?"), SecretLeak.Severity.HIGH),
        // Passwords - only if not in comments/examples
        new SecretPattern("Password", Pattern.compile("(?i)(password|pwd|passwd)\\s*[:=]\\s*['\"]?([^'\"\\s]{12,})['\"]?"), SecretLeak.Severity.MEDIUM),
        // JWT Tokens - must have 3 parts
        new SecretPattern("JWT Token", Pattern.compile("eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}"), SecretLeak.Severity.HIGH),
        // Database URIs with credentials
        new SecretPattern("MongoDB URI", Pattern.compile("mongodb://[^:]+:[^@]+@[^\\s]+"), SecretLeak.Severity.HIGH),
        new SecretPattern("PostgreSQL URI", Pattern.compile("postgres://[^:]+:[^@]+@[^\\s]+"), SecretLeak.Severity.HIGH),
        new SecretPattern("MySQL URI", Pattern.compile("mysql://[^:]+:[^@]+@[^\\s]+"), SecretLeak.Severity.HIGH),
        // Slack Tokens
        new SecretPattern("Slack Token", Pattern.compile("xox[baprs]-[0-9a-zA-Z-]{10,}"), SecretLeak.Severity.HIGH),
        // Stripe Keys
        new SecretPattern("Stripe Key", Pattern.compile("sk_live_[0-9a-zA-Z]{24,}"), SecretLeak.Severity.CRITICAL),
        // Google API Keys
        new SecretPattern("Google API Key", Pattern.compile("AIza[0-9A-Za-z_-]{35}"), SecretLeak.Severity.HIGH),
        // Firebase Keys
        new SecretPattern("Firebase Key", Pattern.compile("AAAA[A-Za-z0-9_-]{140,}"), SecretLeak.Severity.HIGH)
    );
    
    public List<SecretLeak> scanForSecrets(RepositoryDTO repositoryDTO, String githubToken, String gitlabToken) {
        log.info("Scanning for secrets in {}: {}/{}", 
                repositoryDTO.getPlatform(), repositoryDTO.getOwner(), repositoryDTO.getName());
        
        List<SecretLeak> secrets = new ArrayList<>();
        
        try {
            // Get repository files
            List<RepositoryFile> files = getRepositoryFiles(repositoryDTO, githubToken, gitlabToken);
            
            // Filter and scan files in parallel for better performance
            List<CompletableFuture<List<SecretLeak>>> futures = files.stream()
                    .filter(file -> !shouldSkipFile(file.path()))
                    .map(file -> CompletableFuture.supplyAsync(
                            () -> scanFileForSecrets(file),
                            executorService
                    ))
                    .collect(Collectors.toList());
            
            // Collect all results
            for (CompletableFuture<List<SecretLeak>> future : futures) {
                try {
                    secrets.addAll(future.get());
                } catch (Exception e) {
                    log.warn("Error scanning file for secrets", e);
                }
            }
            
        } catch (Exception e) {
            log.error("Error scanning for secrets", e);
        }
        
        return secrets;
    }
    
    private List<RepositoryFile> getRepositoryFiles(RepositoryDTO repositoryDTO, String githubToken, String gitlabToken) {
        if ("github".equalsIgnoreCase(repositoryDTO.getPlatform())) {
            return githubService.getRepositoryFiles(repositoryDTO, githubToken);
        } else {
            return gitLabService.getRepositoryFiles(repositoryDTO, gitlabToken);
        }
    }
    
    private boolean shouldSkipFile(String filePath) {
        // Skip binary files, images, and common non-code files
        String lowerPath = filePath.toLowerCase();
        return lowerPath.endsWith(".png") || lowerPath.endsWith(".jpg") || 
               lowerPath.endsWith(".jpeg") || lowerPath.endsWith(".gif") ||
               lowerPath.endsWith(".pdf") || lowerPath.endsWith(".zip") ||
               lowerPath.endsWith(".tar") || lowerPath.endsWith(".gz") ||
               lowerPath.contains("node_modules") || lowerPath.contains(".git") ||
               lowerPath.contains("vendor") || lowerPath.contains("dist") ||
               lowerPath.contains("build");
    }
    
    private List<SecretLeak> scanFileForSecrets(RepositoryFile file) {
        List<SecretLeak> secrets = new ArrayList<>();
        String content = file.content();
        
        if (content == null || content.isEmpty()) {
            return secrets;
        }
        
        // Skip common false positive patterns
        if (isFalsePositive(content, file.path())) {
            return secrets;
        }
        
        String[] lines = content.split("\n");
        
        for (SecretPattern pattern : SECRET_PATTERNS) {
            Matcher matcher = pattern.pattern.matcher(content);
            int lineNumber = 1;
            
            for (String line : lines) {
                if (pattern.pattern.matcher(line).find()) {
                    // Validate the found secret to reduce false positives
                    if (isValidSecret(pattern, line, file.path())) {
                        SecretLeak leak = new SecretLeak();
                        leak.setType(pattern.name);
                        leak.setFile(file.path());
                        leak.setLine(lineNumber);
                        leak.setSeverity(pattern.severity);
                        leak.setPattern(line.trim());
                        secrets.add(leak);
                    }
                }
                lineNumber++;
            }
        }
        
        return secrets;
    }
    
    private boolean isFalsePositive(String content, String filePath) {
        // Skip test files, examples, and documentation
        String lowerPath = filePath.toLowerCase();
        if (lowerPath.contains("test") || 
            lowerPath.contains("example") || 
            lowerPath.contains("sample") ||
            lowerPath.contains("mock") ||
            lowerPath.contains("fixture") ||
            lowerPath.contains(".md") ||
            lowerPath.contains("readme")) {
            return true;
        }
        
        // Skip if content contains common false positive markers
        String lowerContent = content.toLowerCase();
        if (lowerContent.contains("example") && 
            (lowerContent.contains("fake") || lowerContent.contains("dummy") || lowerContent.contains("placeholder"))) {
            return true;
        }
        
        return false;
    }
    
    private boolean isValidSecret(SecretPattern pattern, String line, String filePath) {
        // Additional validation to reduce false positives
        String lowerLine = line.toLowerCase();
        
        // Skip commented lines (common in examples)
        if (lowerLine.trim().startsWith("//") || 
            lowerLine.trim().startsWith("#") || 
            lowerLine.trim().startsWith("*") ||
            lowerLine.trim().startsWith("<!--")) {
            return false;
        }
        
        // Skip lines with common false positive keywords
        if (lowerLine.contains("example") || 
            lowerLine.contains("sample") || 
            lowerLine.contains("placeholder") ||
            lowerLine.contains("your_") ||
            lowerLine.contains("replace_") ||
            lowerLine.contains("xxx") ||
            lowerLine.contains("fake")) {
            return false;
        }
        
        // Validate AWS Access Key format more strictly
        if (pattern.name.equals("AWS_ACCESS_KEY_ID")) {
            // AWS Access Keys start with AKIA and have specific format
            if (!line.matches(".*AKIA[0-9A-Z]{16}.*")) {
                return false;
            }
        }
        
        // Validate GitHub token format
        if (pattern.name.equals("GitHub Token")) {
            // GitHub tokens are exactly 36 characters after ghp_
            if (!line.matches(".*ghp_[A-Za-z0-9]{36}.*")) {
                return false;
            }
        }
        
        // Validate JWT tokens (should have 3 parts separated by dots)
        if (pattern.name.equals("JWT Token")) {
            String[] parts = line.split("\\.");
            if (parts.length != 3) {
                return false;
            }
        }
        
        return true;
    }
    
    private record SecretPattern(String name, Pattern pattern, SecretLeak.Severity severity) {}
}
