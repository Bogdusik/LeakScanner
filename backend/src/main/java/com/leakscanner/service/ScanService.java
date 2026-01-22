package com.leakscanner.service;

import com.leakscanner.dto.*;
import com.leakscanner.mapper.ScanResultMapper;
import com.leakscanner.model.Repository;
import com.leakscanner.model.ScanResult;
import com.leakscanner.repository.RepositoryRepository;
import com.leakscanner.repository.ScanResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScanService {
    
    private final RepositoryRepository repositoryRepository;
    private final ScanResultRepository scanResultRepository;
    private final SecurityScannerService securityScannerService;
    private final ScanResultMapper scanResultMapper;
    
    @Transactional
    public ScanResultDTO scanRepository(
            RepositoryDTO repositoryDTO,
            String githubToken,
            String gitlabToken,
            String snykToken,
            boolean forceRescan
    ) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Get or create repository
            Repository repository = getOrCreateRepository(repositoryDTO);
            
            // Check for recent scan result (within last hour) to avoid duplicate scans
            // Skip cache check if forceRescan is true
            if (!forceRescan) {
                ScanResult recentResult = scanResultRepository
                        .findTopByRepositoryAndScanStatusOrderByScanDateDesc(repository, ScanResult.ScanStatus.SUCCESS)
                        .orElse(null);
                
                if (recentResult != null && recentResult.getScanDate() != null) {
                    long hoursSinceLastScan = java.time.Duration.between(
                            recentResult.getScanDate(), 
                            LocalDateTime.now()
                    ).toHours();
                    
                    if (hoursSinceLastScan < 1) {
                        log.info("Returning cached scan result for {} (scanned {} hours ago)", 
                                repositoryDTO, hoursSinceLastScan);
                        return scanResultMapper.toDTO(recentResult, repositoryDTO);
                    }
                }
            } else {
                log.info("Force rescan requested for {}", repositoryDTO);
            }
            
            // Perform security scan
            SecurityScanResult scanResult = securityScannerService.performScan(
                    repositoryDTO,
                    githubToken,
                    gitlabToken,
                    snykToken
            );
            
            // Calculate security score
            int securityScore = calculateSecurityScore(scanResult);
            
            // Save scan result
            ScanResult savedResult = saveScanResult(
                    repository,
                    scanResult,
                    securityScore,
                    System.currentTimeMillis() - startTime
            );
            
            return scanResultMapper.toDTO(savedResult, repositoryDTO);
            
        } catch (Exception e) {
            log.error("Error scanning repository: {}", repositoryDTO, e);
            
            // Save failed scan result
            Repository repository = getOrCreateRepository(repositoryDTO);
            ScanResult failedResult = saveFailedScanResult(
                    repository,
                    e.getMessage(),
                    System.currentTimeMillis() - startTime
            );
            
            return scanResultMapper.toDTO(failedResult, repositoryDTO);
        }
    }
    
    @Cacheable(value = "scanHistory", key = "#repositoryDTO.platform + ':' + #repositoryDTO.owner + '/' + #repositoryDTO.name")
    public List<ScanResultDTO> getScanHistory(RepositoryDTO repositoryDTO) {
        String fullName = repositoryDTO.getPlatform() + ":" + repositoryDTO.getOwner() + "/" + repositoryDTO.getName();
        
        return repositoryRepository.findByFullName(fullName)
                .map(repo -> scanResultRepository.findByRepositoryOrderByScanDateDesc(repo)
                        .stream()
                        .map(result -> scanResultMapper.toDTO(result, repositoryDTO))
                        .collect(Collectors.toList()))
                .orElse(List.of());
    }
    
    private Repository getOrCreateRepository(RepositoryDTO dto) {
        String fullName = dto.getPlatform() + ":" + dto.getOwner() + "/" + dto.getName();
        
        return repositoryRepository.findByFullName(fullName)
                .orElseGet(() -> {
                    Repository repo = new Repository();
                    repo.setPlatform(dto.getPlatform());
                    repo.setOwner(dto.getOwner());
                    repo.setName(dto.getName());
                    repo.setFullName(fullName);
                    repo.setCreatedAt(LocalDateTime.now());
                    repo.setUpdatedAt(LocalDateTime.now());
                    return repositoryRepository.save(repo);
                });
    }
    
    private ScanResult saveScanResult(
            Repository repository,
            SecurityScanResult scanResult,
            int securityScore,
            long durationMs
    ) {
        ScanResult result = new ScanResult();
        result.setRepository(repository);
        result.setSecurityScore(securityScore);
        result.setSecretsCount(scanResult.getSecrets().size());
        result.setVulnerabilitiesCount(scanResult.getVulnerabilities().size());
        result.setOutdatedDependenciesCount(scanResult.getOutdatedDependencies().size());
        result.setScanDate(LocalDateTime.now());
        result.setScanDurationMs(durationMs);
        result.setScanStatus(ScanResult.ScanStatus.SUCCESS);
        
        // Set relationships
        scanResult.getSecrets().forEach(secret -> {
            secret.setScanResult(result);
        });
        scanResult.getVulnerabilities().forEach(vuln -> {
            vuln.setScanResult(result);
        });
        scanResult.getOutdatedDependencies().forEach(dep -> {
            dep.setScanResult(result);
        });
        
        result.setSecrets(scanResult.getSecrets());
        result.setVulnerabilities(scanResult.getVulnerabilities());
        result.setOutdatedDependencies(scanResult.getOutdatedDependencies());
        
        return scanResultRepository.save(result);
    }
    
    private ScanResult saveFailedScanResult(Repository repository, String errorMessage, long durationMs) {
        ScanResult result = new ScanResult();
        result.setRepository(repository);
        result.setSecurityScore(0);
        result.setSecretsCount(0);
        result.setVulnerabilitiesCount(0);
        result.setOutdatedDependenciesCount(0);
        result.setScanDate(LocalDateTime.now());
        result.setScanDurationMs(durationMs);
        result.setScanStatus(ScanResult.ScanStatus.FAILED);
        result.setErrorMessage(errorMessage);
        
        return scanResultRepository.save(result);
    }
    
    @Transactional
    public void scanRepositoryStream(
            RepositoryDTO repositoryDTO,
            String githubToken,
            String gitlabToken,
            String snykToken,
            boolean forceRescan,
            Consumer<ScanProgressDTO> progressCallback
    ) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Get or create repository
            Repository repository = getOrCreateRepository(repositoryDTO);
            
            // Check for recent scan result (within last hour) to avoid duplicate scans
            // Skip cache check if forceRescan is true
            if (!forceRescan) {
                ScanResult recentResult = scanResultRepository
                        .findTopByRepositoryAndScanStatusOrderByScanDateDesc(repository, ScanResult.ScanStatus.SUCCESS)
                        .orElse(null);
                
                if (recentResult != null && recentResult.getScanDate() != null) {
                    long hoursSinceLastScan = java.time.Duration.between(
                            recentResult.getScanDate(), 
                            LocalDateTime.now()
                    ).toHours();
                    
                    if (hoursSinceLastScan < 1) {
                        log.info("Returning cached scan result for {} (scanned {} hours ago)", 
                                repositoryDTO, hoursSinceLastScan);
                        ScanResultDTO cachedResult = scanResultMapper.toDTO(recentResult, repositoryDTO);
                        progressCallback.accept(ScanProgressDTO.builder()
                                .type("complete")
                                .progress(100)
                                .status("Completed")
                                .finalResult(cachedResult)
                                .build());
                        return;
                    }
                }
            } else {
                log.info("Force rescan requested for {}", repositoryDTO);
            }
            
            // Perform security scan with streaming
            SecurityScanResult scanResult = securityScannerService.performScanStream(
                    repositoryDTO,
                    githubToken,
                    gitlabToken,
                    snykToken,
                    progressCallback
            );
            
            // Send progress update before calculating score
            progressCallback.accept(ScanProgressDTO.builder()
                    .type("progress")
                    .progress(92)
                    .status("Calculating security score...")
                    .build());
            
            // Calculate security score
            int securityScore = calculateSecurityScore(scanResult);
            
            // Send progress update before saving
            progressCallback.accept(ScanProgressDTO.builder()
                    .type("progress")
                    .progress(95)
                    .status("Saving results...")
                    .build());
            
            // Save scan result (this can be slow, so we show progress)
            ScanResult savedResult = saveScanResult(
                    repository,
                    scanResult,
                    securityScore,
                    System.currentTimeMillis() - startTime
            );
            
            // Send progress update before mapping
            progressCallback.accept(ScanProgressDTO.builder()
                    .type("progress")
                    .progress(98)
                    .status("Finalizing...")
                    .build());
            
            ScanResultDTO finalResult = scanResultMapper.toDTO(savedResult, repositoryDTO);
            
            // CRITICAL: Always send complete event - this is what frontend waits for
            try {
                progressCallback.accept(ScanProgressDTO.builder()
                        .type("complete")
                        .progress(100)
                        .status("Completed")
                        .finalResult(finalResult)
                        .build());
                log.info("Complete event sent successfully with final result");
            } catch (Exception e) {
                log.error("CRITICAL: Failed to send complete event", e);
                // Try one more time
                try {
                    progressCallback.accept(ScanProgressDTO.builder()
                            .type("complete")
                            .progress(100)
                            .status("Completed")
                            .finalResult(finalResult)
                            .build());
                } catch (Exception e2) {
                    log.error("CRITICAL: Failed to send complete event on retry", e2);
                    throw new RuntimeException("Failed to send complete event", e2);
                }
            }
            
        } catch (Exception e) {
            log.error("Error scanning repository: {}", repositoryDTO, e);
            
            // CRITICAL: Always send complete/error event even on failure
            try {
                // Save failed scan result
                Repository repository = getOrCreateRepository(repositoryDTO);
                ScanResult failedResult = saveFailedScanResult(
                        repository,
                        e.getMessage(),
                        System.currentTimeMillis() - startTime
                );
                
                ScanResultDTO failedDTO = scanResultMapper.toDTO(failedResult, repositoryDTO);
                progressCallback.accept(ScanProgressDTO.builder()
                        .type("complete")
                        .progress(100)
                        .status("Failed")
                        .message("Scan failed: " + e.getMessage())
                        .finalResult(failedDTO)
                        .build());
                log.info("Error event sent successfully as complete event");
            } catch (Exception callbackError) {
                log.error("CRITICAL: Failed to send error event to callback", callbackError);
                // Last resort: send minimal error event
                try {
                    progressCallback.accept(ScanProgressDTO.builder()
                            .type("error")
                            .progress(0)
                            .status("Failed")
                            .message("Scan failed: " + e.getMessage())
                            .build());
                } catch (Exception finalError) {
                    log.error("CRITICAL: Completely failed to send any event", finalError);
                }
            }
        }
    }
    
    private int calculateSecurityScore(SecurityScanResult scanResult) {
        int score = 100;
        
        // Deduct points for secrets
        score -= scanResult.getSecrets().stream()
                .mapToInt(s -> switch (s.getSeverity()) {
                    case CRITICAL -> 20;
                    case HIGH -> 10;
                    case MEDIUM -> 5;
                    case LOW -> 2;
                })
                .sum();
        
        // Deduct points for vulnerabilities
        score -= scanResult.getVulnerabilities().stream()
                .mapToInt(v -> switch (v.getSeverity()) {
                    case CRITICAL -> 15;
                    case HIGH -> 8;
                    case MEDIUM -> 4;
                    case LOW -> 1;
                })
                .sum();
        
        // Deduct points for outdated dependencies
        score -= Math.min(scanResult.getOutdatedDependencies().size() * 2, 20);
        
        return Math.max(0, Math.min(100, score));
    }
}
