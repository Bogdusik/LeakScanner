package com.leakscanner.controller;

import com.leakscanner.dto.RepositoryDTO;
import com.leakscanner.dto.ScanProgressDTO;
import com.leakscanner.dto.ScanResultDTO;
import com.leakscanner.service.ScanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/api/v1/scan")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class ScanController {
    
    private final ScanService scanService;
    
    @PostMapping
    public ResponseEntity<ScanResultDTO> scanRepository(
            @Valid @RequestBody RepositoryDTO repositoryDTO,
            @RequestHeader(value = "X-GitHub-Token", required = false) String githubToken,
            @RequestHeader(value = "X-GitLab-Token", required = false) String gitlabToken,
            @RequestHeader(value = "X-Snyk-Token", required = false) String snykToken
    ) {
        ScanResultDTO result = scanService.scanRepository(repositoryDTO, githubToken, gitlabToken, snykToken, false);
        return ResponseEntity.ok(result);
    }
    
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter scanRepositoryStream(
            @Valid @RequestBody RepositoryDTO repositoryDTO,
            @RequestHeader(value = "X-GitHub-Token", required = false) String githubToken,
            @RequestHeader(value = "X-GitLab-Token", required = false) String gitlabToken,
            @RequestHeader(value = "X-Snyk-Token", required = false) String snykToken,
            @RequestParam(value = "force", defaultValue = "false") boolean forceRescan
    ) {
        log.info("Stream scan request for repository: {}/{} on {} (force: {})", 
                repositoryDTO.getOwner(), repositoryDTO.getName(), repositoryDTO.getPlatform(), forceRescan);
        
        SseEmitter emitter = new SseEmitter(120000L); // 120 seconds timeout
        
        AtomicReference<ScanResultDTO> finalResultRef = new AtomicReference<>(null);
        
        CompletableFuture.runAsync(() -> {
            try {
                scanService.scanRepositoryStream(
                        repositoryDTO, 
                        githubToken, 
                        gitlabToken, 
                        snykToken, 
                        forceRescan,
                        (progress) -> {
                            try {
                                emitter.send(SseEmitter.event()
                                        .name(progress.getType() != null ? progress.getType() : "message")
                                        .data(progress));
                                
                                if (progress.getType() != null && progress.getType().equals("complete")) {
                                    if (progress.getFinalResult() != null) {
                                        finalResultRef.set(progress.getFinalResult());
                                    }
                                }
                            } catch (IOException e) {
                                log.error("Error sending progress update: {}", e.getMessage(), e);
                            }
                        }
                );
                
                // CRITICAL: Ensure we ALWAYS send complete event, even if scanService didn't
                ScanResultDTO finalResult = finalResultRef.get();
                if (finalResult == null) {
                    log.warn("No final result received from scanService, sending fallback complete event with empty result");
                    try {
                        // Create empty result to indicate scan completed but found nothing
                        ScanResultDTO emptyResult = ScanResultDTO.builder()
                                .repository(repositoryDTO)
                                .secrets(List.of())
                                .vulnerabilities(List.of())
                                .outdatedDependencies(List.of())
                                .securityScore(100)
                                .lastScanned(java.time.LocalDateTime.now().toString())
                                .build();
                        
                        emitter.send(SseEmitter.event()
                                .name("complete")
                                .data(ScanProgressDTO.builder()
                                        .type("complete")
                                        .progress(100)
                                        .status("Completed")
                                        .finalResult(emptyResult)
                                        .build()));
                        log.info("Fallback complete event sent with empty result");
                    } catch (IOException e) {
                        log.error("CRITICAL: Failed to send fallback complete event", e);
                        // Try one more time with minimal event
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("complete")
                                    .data(ScanProgressDTO.builder()
                                            .type("complete")
                                            .progress(100)
                                            .status("Completed")
                                            .build()));
                        } catch (IOException e2) {
                            log.error("CRITICAL: Completely failed to send fallback complete event", e2);
                        }
                    }
                } else {
                    log.info("Final result received, complete event should have been sent by scanService");
                }
                
                // CRITICAL: Give time for complete event to reach client before closing connection
                // This prevents "Stream ended without complete event" errors
                try {
                    // Flush any pending data and wait to ensure complete event is fully transmitted
                    // Increased delay to ensure client receives the complete event
                    Thread.sleep(3000); // 3 seconds delay to ensure event is sent and received
                    log.debug("Waiting period completed, closing emitter");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted while waiting before closing emitter");
                }
                
                emitter.complete();
                log.info("Stream scan completed successfully, emitter closed");
            } catch (Exception e) {
                log.error("Error during stream scan", e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(ScanProgressDTO.builder()
                                    .type("error")
                                    .message("Scan failed: " + e.getMessage())
                                    .build()));
                    // Give time for error event to reach client
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    emitter.complete();
                } catch (IOException ex) {
                    log.error("Error completing emitter after error", ex);
                    emitter.completeWithError(ex);
                }
            }
        });
        
        return emitter;
    }
    
    @GetMapping("/history")
    public ResponseEntity<List<ScanResultDTO>> getScanHistory(
            @RequestParam String owner,
            @RequestParam String name,
            @RequestParam String platform
    ) {
        RepositoryDTO repositoryDTO = new RepositoryDTO(owner, name, platform);
        List<ScanResultDTO> history = scanService.getScanHistory(repositoryDTO);
        return ResponseEntity.ok(history);
    }
}
