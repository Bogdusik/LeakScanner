package com.leakscanner.service;

import com.leakscanner.dto.RepositoryDTO;
import com.leakscanner.model.OutdatedDependency;
import com.leakscanner.model.SecretLeak;
import com.leakscanner.model.Vulnerability;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityScannerService {
    
    private final SecretScannerService secretScannerService;
    private final VulnerabilityScannerService vulnerabilityScannerService;
    private final DependencyScannerService dependencyScannerService;
    private final ExecutorService executorService = Executors.newFixedThreadPool(3);
    
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down SecurityScannerService executor service");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    log.warn("Executor service did not terminate");
                }
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    public SecurityScanResult performScan(
            RepositoryDTO repositoryDTO,
            String githubToken,
            String gitlabToken,
            String snykToken
    ) {
        log.info("Starting security scan for {}: {}/{}", 
                repositoryDTO.getPlatform(), repositoryDTO.getOwner(), repositoryDTO.getName());
        
        SecurityScanResult.SecurityScanResultBuilder resultBuilder = SecurityScanResult.builder();
        
        // Run scans in parallel
        CompletableFuture<List<SecretLeak>> secretsFuture = CompletableFuture.supplyAsync(
                () -> secretScannerService.scanForSecrets(repositoryDTO, githubToken, gitlabToken),
                executorService
        );
        
        CompletableFuture<List<Vulnerability>> vulnerabilitiesFuture = CompletableFuture.supplyAsync(
                () -> vulnerabilityScannerService.scanForVulnerabilities(repositoryDTO, githubToken, gitlabToken, snykToken),
                executorService
        );
        
        CompletableFuture<List<OutdatedDependency>> dependenciesFuture = CompletableFuture.supplyAsync(
                () -> dependencyScannerService.scanForOutdatedDependencies(repositoryDTO, githubToken, gitlabToken),
                executorService
        );
        
        // Wait for all scans to complete with aggressive timeout protection
        // Use shorter timeout (40 seconds) and handle each future separately
        long startTime = System.currentTimeMillis();
        try {
            log.info("Waiting for scan results with 40 second timeout...");
            
            // Get results with individual timeouts and error handling (reduced to 40 seconds)
            List<SecretLeak> secrets = getWithTimeout(secretsFuture, "secrets", 40);
            List<Vulnerability> vulnerabilities = getWithTimeout(vulnerabilitiesFuture, "vulnerabilities", 40);
            List<OutdatedDependency> outdatedDependencies = getWithTimeout(dependenciesFuture, "dependencies", 40);
            
            // Ensure all results are non-null
            resultBuilder.secrets(secrets != null ? secrets : new ArrayList<>());
            resultBuilder.vulnerabilities(vulnerabilities != null ? vulnerabilities : new ArrayList<>());
            resultBuilder.outdatedDependencies(outdatedDependencies != null ? outdatedDependencies : new ArrayList<>());
            
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("Security scan completed in {}ms. Found {} secrets, {} vulnerabilities, {} outdated dependencies",
                    elapsed,
                    resultBuilder.build().getSecrets().size(), 
                    resultBuilder.build().getVulnerabilities().size(), 
                    resultBuilder.build().getOutdatedDependencies().size());
            
        } catch (Exception e) {
            log.error("Unexpected error during scan execution", e);
            // Ensure we always return a result, even if there were errors
            resultBuilder.secrets(new ArrayList<>());
            resultBuilder.vulnerabilities(new ArrayList<>());
            resultBuilder.outdatedDependencies(new ArrayList<>());
        } finally {
            // Aggressively cancel any remaining futures
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed > 45000) {
                log.warn("Scan took too long ({}ms), forcing cancellation", elapsed);
            }
            
            // Force cancel all futures
            secretsFuture.cancel(true);
            vulnerabilitiesFuture.cancel(true);
            dependenciesFuture.cancel(true);
            
            // Wait a bit for cancellation to take effect
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        return resultBuilder.build();
    }
    
    @SuppressWarnings("unchecked")
    private <T> T getWithTimeout(CompletableFuture<T> future, String scanType, long timeoutSeconds) {
        try {
            log.debug("Waiting for {} scan result...", scanType);
            T result = future.get(timeoutSeconds, TimeUnit.SECONDS);
            log.debug("{} scan completed successfully", scanType);
            return result != null ? result : (T) getDefaultResult(scanType);
        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("{} scan timed out after {} seconds, returning empty result", scanType, timeoutSeconds);
            future.cancel(true);
            return (T) getDefaultResult(scanType);
        } catch (Exception e) {
            log.error("Error during {} scan", scanType, e);
            future.cancel(true);
            return (T) getDefaultResult(scanType);
        }
    }
    
    private Object getDefaultResult(String scanType) {
        return switch (scanType) {
            case "secrets" -> new ArrayList<SecretLeak>();
            case "vulnerabilities" -> new ArrayList<Vulnerability>();
            case "dependencies" -> new ArrayList<OutdatedDependency>();
            default -> Collections.emptyList();
        };
    }
}
