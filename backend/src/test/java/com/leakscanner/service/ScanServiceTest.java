package com.leakscanner.service;

import com.leakscanner.dto.RepositoryDTO;
import com.leakscanner.dto.ScanResultDTO;
import com.leakscanner.mapper.ScanResultMapper;
import com.leakscanner.model.Repository;
import com.leakscanner.model.ScanResult;
import com.leakscanner.repository.RepositoryRepository;
import com.leakscanner.repository.ScanResultRepository;
import com.leakscanner.service.SecurityScanResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScanServiceTest {

    @Mock
    private RepositoryRepository repositoryRepository;

    @Mock
    private ScanResultRepository scanResultRepository;

    @Mock
    private SecurityScannerService securityScannerService;

    @Mock
    private ScanResultMapper scanResultMapper;

    @InjectMocks
    private ScanService scanService;

    private RepositoryDTO repositoryDTO;
    private Repository repository;
    private SecurityScanResult securityScanResult;

    @BeforeEach
    void setUp() {
        repositoryDTO = new RepositoryDTO("octocat", "Hello-World", "github");
        
        repository = new Repository();
        repository.setId(1L);
        repository.setPlatform("github");
        repository.setOwner("octocat");
        repository.setName("Hello-World");
        repository.setFullName("github:octocat/Hello-World");
        
        securityScanResult = SecurityScanResult.builder()
                .secrets(new ArrayList<>())
                .vulnerabilities(new ArrayList<>())
                .outdatedDependencies(new ArrayList<>())
                .build();
    }

    @Test
    void testScanRepository_NewRepository() {
        // Given
        when(repositoryRepository.findByFullName(anyString())).thenReturn(Optional.empty());
        when(repositoryRepository.save(any(Repository.class))).thenReturn(repository);
        when(scanResultRepository.findTopByRepositoryAndScanStatusOrderByScanDateDesc(
                any(Repository.class), any(ScanResult.ScanStatus.class)))
                .thenReturn(Optional.empty());
        when(securityScannerService.performScan(any(), any(), any(), any()))
                .thenReturn(securityScanResult);
        
        ScanResult savedResult = new ScanResult();
        savedResult.setId(1L);
        savedResult.setSecurityScore(100);
        when(scanResultRepository.save(any(ScanResult.class))).thenReturn(savedResult);
        
        ScanResultDTO resultDTO = new ScanResultDTO();
        resultDTO.setSecurityScore(100);
        when(scanResultMapper.toDTO(any(ScanResult.class), any(RepositoryDTO.class)))
                .thenReturn(resultDTO);

        // When
        ScanResultDTO result = scanService.scanRepository(repositoryDTO, null, null, null, false);

        // Then
        assertNotNull(result);
        assertEquals(100, result.getSecurityScore());
        verify(repositoryRepository, times(1)).save(any(Repository.class));
        verify(securityScannerService, times(1)).performScan(any(), any(), any(), any());
    }

    @Test
    void testScanRepository_WithCachedResult() {
        // Given
        when(repositoryRepository.findByFullName(anyString())).thenReturn(Optional.of(repository));
        
        ScanResult cachedResult = new ScanResult();
        cachedResult.setId(1L);
        cachedResult.setSecurityScore(85);
        cachedResult.setScanDate(LocalDateTime.now().minusMinutes(30));
        cachedResult.setScanStatus(ScanResult.ScanStatus.SUCCESS);
        
        when(scanResultRepository.findTopByRepositoryAndScanStatusOrderByScanDateDesc(
                any(Repository.class), any(ScanResult.ScanStatus.class)))
                .thenReturn(Optional.of(cachedResult));
        
        ScanResultDTO resultDTO = new ScanResultDTO();
        resultDTO.setSecurityScore(85);
        when(scanResultMapper.toDTO(any(ScanResult.class), any(RepositoryDTO.class)))
                .thenReturn(resultDTO);

        // When
        ScanResultDTO result = scanService.scanRepository(repositoryDTO, null, null, null, false);

        // Then
        assertNotNull(result);
        assertEquals(85, result.getSecurityScore());
        // Should not perform new scan
        verify(securityScannerService, never()).performScan(any(), any(), any(), any());
    }

    @Test
    void testScanRepository_ForceRescan() {
        // Given
        when(repositoryRepository.findByFullName(anyString())).thenReturn(Optional.of(repository));
        // Force rescan should skip cache check, so we don't need to mock findTopByRepository
        when(securityScannerService.performScan(any(), any(), any(), any()))
                .thenReturn(securityScanResult);
        
        ScanResult savedResult = new ScanResult();
        savedResult.setId(1L);
        savedResult.setSecurityScore(100);
        when(scanResultRepository.save(any(ScanResult.class))).thenReturn(savedResult);
        
        ScanResultDTO resultDTO = new ScanResultDTO();
        resultDTO.setSecurityScore(100);
        when(scanResultMapper.toDTO(any(ScanResult.class), any(RepositoryDTO.class)))
                .thenReturn(resultDTO);

        // When
        ScanResultDTO result = scanService.scanRepository(repositoryDTO, null, null, null, true);

        // Then
        assertNotNull(result);
        // Should perform scan even if cached result exists
        verify(securityScannerService, times(1)).performScan(any(), any(), any(), any());
    }

    @Test
    void testScanRepository_WithError() {
        // Given
        when(repositoryRepository.findByFullName(anyString())).thenReturn(Optional.of(repository));
        when(scanResultRepository.findTopByRepositoryAndScanStatusOrderByScanDateDesc(
                any(Repository.class), any(ScanResult.ScanStatus.class)))
                .thenReturn(Optional.empty());
        when(securityScannerService.performScan(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Scan failed"));
        
        ScanResult failedResult = new ScanResult();
        failedResult.setId(1L);
        failedResult.setSecurityScore(0);
        failedResult.setScanStatus(ScanResult.ScanStatus.FAILED);
        when(scanResultRepository.save(any(ScanResult.class))).thenReturn(failedResult);
        
        ScanResultDTO resultDTO = new ScanResultDTO();
        resultDTO.setSecurityScore(0);
        when(scanResultMapper.toDTO(any(ScanResult.class), any(RepositoryDTO.class)))
                .thenReturn(resultDTO);

        // When
        ScanResultDTO result = scanService.scanRepository(repositoryDTO, null, null, null, false);

        // Then
        assertNotNull(result);
        assertEquals(0, result.getSecurityScore());
        verify(scanResultRepository, times(1)).save(any(ScanResult.class));
    }
}
