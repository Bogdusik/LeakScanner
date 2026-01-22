package com.leakscanner.controller;

import com.leakscanner.dto.RepositoryDTO;
import com.leakscanner.dto.ScanProgressDTO;
import com.leakscanner.dto.ScanResultDTO;
import com.leakscanner.service.ScanService;
import com.leakscanner.service.SecurityScannerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/scan")
@RequiredArgsConstructor
@Slf4j
@Validated
public class ScanController {
    
    private final ScanService scanService;
    private final SecurityScannerService securityScannerService;
    
    @PostMapping
    public ResponseEntity<ScanResultDTO> scanRepository(
            @Valid @RequestBody RepositoryDTO repositoryDTO,
            @RequestHeader(value = "X-GitHub-Token", required = false) String githubToken,
            @RequestHeader(value = "X-GitLab-Token", required = false) String gitlabToken,
            @RequestHeader(value = "X-Snyk-Token", required = false) String snykToken,
            @RequestParam(value = "force", defaultValue = "false") boolean forceRescan
    ) {
        // Log scan request without exposing tokens
        log.info("Scan request for repository: {}/{} on {} (force: {})", 
                repositoryDTO.getOwner(), repositoryDTO.getName(), repositoryDTO.getPlatform(), forceRescan);
        
        ScanResultDTO result = scanService.scanRepository(repositoryDTO, githubToken, gitlabToken, snykToken, forceRescan);
        return ResponseEntity.ok(result);
    }
    
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @CrossOrigin(origins = "*")
    public SseEmitter scanRepositoryStream(
            @Valid @RequestBody RepositoryDTO repositoryDTO,
            @RequestHeader(value = "X-GitHub-Token", required = false) String githubToken,
            @RequestHeader(value = "X-GitLab-Token", required = false) String gitlabToken,
            @RequestHeader(value = "X-Snyk-Token", required = false) String snykToken,
            @RequestParam(value = "force", defaultValue = "false") boolean forceRescan
    ) {
        log.info("Stream scan request for repository: {}/{} on {} (force: {})", 
                repositoryDTO.getOwner(), repositoryDTO.getName(), repositoryDTO.getPlatform(), forceRescan);
        
        SseEmitter emitter = new SseEmitter(90000L); // 90 seconds timeout (increased for large repos)
        
        // Run scan asynchronously and send progress updates
        CompletableFuture.runAsync(() -> {
            java.util.concurrent.atomic.AtomicReference<ScanResultDTO> finalResultRef = new java.util.concurrent.atomic.AtomicReference<>(null);
            try {
                // Send initial progress
                sendProgress(emitter, ScanProgressDTO.builder()
                        .type("progress")
                        .progress(5)
                        .status("Starting scan...")
                        .secrets(new java.util.ArrayList<>())
                        .vulnerabilities(new java.util.ArrayList<>())
                        .outdatedDependencies(new java.util.ArrayList<>())
                        .build());
                
                // Perform scan with progress callbacks
                scanService.scanRepositoryStream(
                        repositoryDTO, 
                        githubToken, 
                        gitlabToken, 
                        snykToken, 
                        forceRescan,
                        (progress) -> {
                            try {
                                sendProgress(emitter, progress);
                                // Store final result when complete event is received
                                if (progress.getType() != null && progress.getType().equals("complete")) {
                                    if (progress.getFinalResult() != null) {
                                        finalResultRef.set(progress.getFinalResult());
                                    }
                                    log.info("Complete event received and sent, finalResult: {}", finalResultRef.get() != null ? "present" : "null");
                                }
                            } catch (Exception e) {
                                log.error("Error sending progress update: {}", e.getMessage(), e);
                                // Don't fail completely, continue processing
                            }
                        }
                );
                
                // Ensure we send complete event even if scanService didn't
                // This should not happen if scanService works correctly, but it's a safety net
                ScanResultDTO finalResult = finalResultRef.get();
                if (finalResult == null) {
                    log.error("CRITICAL: No final result received from scanService! This should not happen.");
                    // Try to send complete event with empty result as fallback
                    // This ensures frontend always gets a complete event
                    try {
                        sendProgress(emitter, ScanProgressDTO.builder()
                                .type("complete")
                                .progress(100)
                                .status("Completed")
                                .finalResult(null)
                                .build());
                        log.warn("Sent fallback complete event without finalResult - frontend will use accumulated data");
                    } catch (Exception e) {
                        log.error("Failed to send fallback complete event", e);
                        // Even if sendProgress fails, we still need to complete the emitter
                    }
                } else {
                    log.info("Final result received and will be sent in complete event");
                }
                
                // Always complete the emitter to close the connection
                // This is critical - without this, frontend will wait forever
                try {
                    emitter.complete();
                    log.info("Stream scan completed successfully, emitter closed");
                } catch (Exception e) {
                    log.error("CRITICAL: Error completing emitter - connection may hang!", e);
                    // Try one more time
                    try {
                        emitter.completeWithError(e);
                    } catch (Exception e2) {
                        log.error("CRITICAL: Failed to complete emitter with error", e2);
                    }
                }
            } catch (Exception e) {
                log.error("Error during stream scan", e);
                try {
                    sendProgress(emitter, ScanProgressDTO.builder()
                            .type("error")
                            .message("Scan failed: " + e.getMessage())
                            .build());
                    emitter.complete();
                } catch (IOException ex) {
                    log.error("Error completing emitter after error", ex);
                    try {
                        emitter.completeWithError(ex);
                    } catch (Exception e2) {
                        log.error("Failed to complete emitter with error", e2);
                    }
                }
            }
        });
        
        return emitter;
    }
    
    private void sendProgress(SseEmitter emitter, ScanProgressDTO progress) throws IOException {
        try {
            emitter.send(SseEmitter.event()
                    .name(progress.getType() != null ? progress.getType() : "message")
                    .data(progress));
        } catch (Exception e) {
            log.error("Error sending progress event", e);
            throw new IOException("Failed to send progress", e);
        }
    }
    
    @GetMapping("/history")
    public ResponseEntity<List<ScanResultDTO>> getScanHistory(
            @RequestParam @NotBlank(message = "Owner is required") 
            @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Owner contains invalid characters") String owner,
            @RequestParam @NotBlank(message = "Repository name is required")
            @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Repository name contains invalid characters") String name,
            @RequestParam @NotBlank(message = "Platform is required")
            @Pattern(regexp = "^(github|gitlab)$", message = "Platform must be 'github' or 'gitlab'") String platform
    ) {
        RepositoryDTO repositoryDTO = new RepositoryDTO(owner, name, platform);
        List<ScanResultDTO> history = scanService.getScanHistory(repositoryDTO);
        return ResponseEntity.ok(history);
    }
}
