package com.leakscanner.mapper;

import com.leakscanner.dto.*;
import com.leakscanner.model.*;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

@Component
public class ScanResultMapper {
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;
    
    public ScanResultDTO toDTO(ScanResult scanResult, RepositoryDTO repositoryDTO) {
        ScanResultDTO.ScanResultDTOBuilder builder = ScanResultDTO.builder()
                .repository(repositoryDTO)
                .securityScore(scanResult.getSecurityScore())
                .lastScanned(scanResult.getScanDate().format(DATE_FORMATTER));
        
        if (scanResult.getScanStatus() == ScanResult.ScanStatus.FAILED) {
            builder.error(scanResult.getErrorMessage());
        }
        
        if (scanResult.getSecrets() != null) {
            builder.secrets(scanResult.getSecrets().stream()
                    .map(this::toSecretLeakDTO)
                    .collect(Collectors.toList()));
        }
        
        if (scanResult.getVulnerabilities() != null) {
            builder.vulnerabilities(scanResult.getVulnerabilities().stream()
                    .map(this::toVulnerabilityDTO)
                    .collect(Collectors.toList()));
        }
        
        if (scanResult.getOutdatedDependencies() != null) {
            builder.outdatedDependencies(scanResult.getOutdatedDependencies().stream()
                    .map(this::toOutdatedDependencyDTO)
                    .collect(Collectors.toList()));
        }
        
        return builder.build();
    }
    
    private SecretLeakDTO toSecretLeakDTO(SecretLeak secret) {
        return new SecretLeakDTO(
                secret.getType(),
                secret.getFile(),
                secret.getLine(),
                secret.getSeverity().name(),
                secret.getPattern()
        );
    }
    
    private VulnerabilityDTO toVulnerabilityDTO(Vulnerability vuln) {
        return new VulnerabilityDTO(
                vuln.getTitle(),
                vuln.getDescription(),
                vuln.getSeverity().name(),
                vuln.getPackageName(),
                vuln.getCve(),
                vuln.getUrl()
        );
    }
    
    private OutdatedDependencyDTO toOutdatedDependencyDTO(OutdatedDependency dep) {
        return new OutdatedDependencyDTO(
                dep.getName(),
                dep.getCurrentVersion(),
                dep.getLatestVersion(),
                dep.getType().name()
        );
    }
}
