package com.leakscanner.controller;

import com.leakscanner.dto.RepositoryDTO;
import com.leakscanner.dto.ScanResultDTO;
import com.leakscanner.service.ScanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/scan")
@RequiredArgsConstructor
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
        ScanResultDTO result = scanService.scanRepository(repositoryDTO, githubToken, gitlabToken, snykToken);
        return ResponseEntity.ok(result);
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
