package com.leakscanner.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanProgressDTO {
    private String type; // "secrets", "vulnerabilities", "dependencies", "progress", "complete"
    @Builder.Default
    private List<SecretLeakDTO> secrets = new ArrayList<>();
    @Builder.Default
    private List<VulnerabilityDTO> vulnerabilities = new ArrayList<>();
    @Builder.Default
    private List<OutdatedDependencyDTO> outdatedDependencies = new ArrayList<>();
    private Integer progress; // 0-100
    private String status;
    private String message;
    private ScanResultDTO finalResult; // Only set when type is "complete"
}
