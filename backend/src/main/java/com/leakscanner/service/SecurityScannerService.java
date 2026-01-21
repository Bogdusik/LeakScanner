package com.leakscanner.service;

import com.leakscanner.dto.RepositoryDTO;
import com.leakscanner.model.OutdatedDependency;
import com.leakscanner.model.SecretLeak;
import com.leakscanner.model.Vulnerability;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityScannerService {
    
    private final SecretScannerService secretScannerService;
    private final VulnerabilityScannerService vulnerabilityScannerService;
    private final DependencyScannerService dependencyScannerService;
    private final ExecutorService executorService = Executors.newFixedThreadPool(3);
    
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
        
        // Wait for all scans to complete
        try {
            List<SecretLeak> secrets = secretsFuture.get();
            List<Vulnerability> vulnerabilities = vulnerabilitiesFuture.get();
            List<OutdatedDependency> outdatedDependencies = dependenciesFuture.get();
            
            resultBuilder.secrets(secrets);
            resultBuilder.vulnerabilities(vulnerabilities);
            resultBuilder.outdatedDependencies(outdatedDependencies);
            
            log.info("Security scan completed. Found {} secrets, {} vulnerabilities, {} outdated dependencies",
                    secrets.size(), vulnerabilities.size(), outdatedDependencies.size());
            
        } catch (Exception e) {
            log.error("Error during parallel scan execution", e);
            resultBuilder.secrets(new ArrayList<>());
            resultBuilder.vulnerabilities(new ArrayList<>());
            resultBuilder.outdatedDependencies(new ArrayList<>());
        }
        
        return resultBuilder.build();
    }
}
