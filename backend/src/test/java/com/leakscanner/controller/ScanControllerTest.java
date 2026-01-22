package com.leakscanner.controller;

import com.leakscanner.dto.RepositoryDTO;
import com.leakscanner.dto.ScanResultDTO;
import com.leakscanner.service.ScanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ScanController.class)
class ScanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ScanService scanService;

    private RepositoryDTO repositoryDTO;
    private ScanResultDTO scanResultDTO;

    @BeforeEach
    void setUp() {
        repositoryDTO = new RepositoryDTO("octocat", "Hello-World", "github");
        
        scanResultDTO = new ScanResultDTO();
        scanResultDTO.setRepository(repositoryDTO);
        scanResultDTO.setSecurityScore(100);
        scanResultDTO.setSecrets(java.util.Collections.emptyList());
        scanResultDTO.setVulnerabilities(java.util.Collections.emptyList());
        scanResultDTO.setOutdatedDependencies(java.util.Collections.emptyList());
    }

    @Test
    void testScanRepository_Success() throws Exception {
        // Given
        when(scanService.scanRepository(any(RepositoryDTO.class), any(), any(), any(), anyBoolean()))
                .thenReturn(scanResultDTO);

        // When & Then
        mockMvc.perform(post("/api/v1/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\":\"octocat\",\"name\":\"Hello-World\",\"platform\":\"github\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.securityScore").value(100))
                .andExpect(jsonPath("$.repository.owner").value("octocat"))
                .andExpect(jsonPath("$.repository.name").value("Hello-World"));
    }

    @Test
    void testScanRepository_InvalidInput() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\":\"\",\"name\":\"Hello-World\",\"platform\":\"github\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testScanRepository_WithGitHubToken() throws Exception {
        // Given
        when(scanService.scanRepository(any(RepositoryDTO.class), any(), any(), any(), anyBoolean()))
                .thenReturn(scanResultDTO);

        // When & Then
        mockMvc.perform(post("/api/v1/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Token", "ghp_test123")
                        .content("{\"owner\":\"octocat\",\"name\":\"Hello-World\",\"platform\":\"github\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.securityScore").exists());
    }
}
