package com.leakscanner.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanResultDTO {
    private RepositoryDTO repository;
    private List<SecretLeakDTO> secrets;
    private List<VulnerabilityDTO> vulnerabilities;
    private List<OutdatedDependencyDTO> outdatedDependencies;
    private Integer securityScore;
    private String lastScanned;
    private String error;
}
