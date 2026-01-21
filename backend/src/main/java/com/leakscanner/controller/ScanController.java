package com.leakscanner.controller;

import com.leakscanner.dto.RepositoryDTO;
import com.leakscanner.dto.ScanResultDTO;
import com.leakscanner.service.ScanService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/scan")
@RequiredArgsConstructor
@Slf4j
@Validated
public class ScanController {
    
    private final ScanService scanService;
    
    @PostMapping
    public ResponseEntity<ScanResultDTO> scanRepository(
            @Valid @RequestBody RepositoryDTO repositoryDTO,
            @RequestHeader(value = "X-GitHub-Token", required = false) String githubToken,
            @RequestHeader(value = "X-GitLab-Token", required = false) String gitlabToken,
            @RequestHeader(value = "X-Snyk-Token", required = false) String snykToken
    ) {
        // Log scan request without exposing tokens
        log.info("Scan request for repository: {}/{} on {}", 
                repositoryDTO.getOwner(), repositoryDTO.getName(), repositoryDTO.getPlatform());
        
        ScanResultDTO result = scanService.scanRepository(repositoryDTO, githubToken, gitlabToken, snykToken);
        return ResponseEntity.ok(result);
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
