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
    
    // Common secret patterns
    private static final List<SecretPattern> SECRET_PATTERNS = List.of(
        new SecretPattern("AWS_ACCESS_KEY_ID", Pattern.compile("AKIA[0-9A-Z]{16}"), SecretLeak.Severity.CRITICAL),
        new SecretPattern("AWS_SECRET_ACCESS_KEY", Pattern.compile("(?i)aws[_-]?secret[_-]?access[_-]?key\\s*[:=]\\s*([A-Za-z0-9/+=]{40})"), SecretLeak.Severity.CRITICAL),
        new SecretPattern("GitHub Token", Pattern.compile("ghp_[A-Za-z0-9]{36}"), SecretLeak.Severity.HIGH),
        new SecretPattern("GitLab Token", Pattern.compile("glpat-[A-Za-z0-9_-]{20}"), SecretLeak.Severity.HIGH),
        new SecretPattern("Private Key", Pattern.compile("-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----"), SecretLeak.Severity.CRITICAL),
        new SecretPattern("API Key", Pattern.compile("(?i)(api[_-]?key|apikey)\\s*[:=]\\s*['\"]?([A-Za-z0-9_-]{20,})['\"]?"), SecretLeak.Severity.HIGH),
        new SecretPattern("Password", Pattern.compile("(?i)(password|pwd|passwd)\\s*[:=]\\s*['\"]?([^'\"\\s]{8,})['\"]?"), SecretLeak.Severity.MEDIUM),
        new SecretPattern("JWT Token", Pattern.compile("eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}"), SecretLeak.Severity.HIGH),
        new SecretPattern("MongoDB URI", Pattern.compile("mongodb://[^\\s]+"), SecretLeak.Severity.HIGH),
        new SecretPattern("PostgreSQL URI", Pattern.compile("postgres://[^\\s]+"), SecretLeak.Severity.HIGH),
        new SecretPattern("Slack Token", Pattern.compile("xox[baprs]-[0-9a-zA-Z-]{10,}"), SecretLeak.Severity.HIGH),
        new SecretPattern("Stripe Key", Pattern.compile("sk_live_[0-9a-zA-Z]{24,}"), SecretLeak.Severity.CRITICAL)
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
        
        String[] lines = content.split("\n");
        
        for (SecretPattern pattern : SECRET_PATTERNS) {
            Matcher matcher = pattern.pattern.matcher(content);
            int lineNumber = 1;
            
            for (String line : lines) {
                if (pattern.pattern.matcher(line).find()) {
                    SecretLeak leak = new SecretLeak();
                    leak.setType(pattern.name);
                    leak.setFile(file.path());
                    leak.setLine(lineNumber);
                    leak.setSeverity(pattern.severity);
                    leak.setPattern(line.trim());
                    secrets.add(leak);
                }
                lineNumber++;
            }
        }
        
        return secrets;
    }
    
    private record SecretPattern(String name, Pattern pattern, SecretLeak.Severity severity) {}
}
