package com.leakscanner.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "scan_results", indexes = {
    @Index(name = "idx_repository_scan_date", columnList = "repository_id,scan_date")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScanResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    private Repository repository;
    
    @Column(name = "security_score", nullable = false)
    private Integer securityScore;
    
    @Column(name = "secrets_count", nullable = false)
    private Integer secretsCount;
    
    @Column(name = "vulnerabilities_count", nullable = false)
    private Integer vulnerabilitiesCount;
    
    @Column(name = "outdated_dependencies_count", nullable = false)
    private Integer outdatedDependenciesCount;
    
    @Column(name = "scan_date", nullable = false)
    private LocalDateTime scanDate;
    
    @Column(name = "scan_duration_ms")
    private Long scanDurationMs;
    
    @Column(name = "scan_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private ScanStatus scanStatus;
    
    @Column(name = "error_message", length = 1000)
    private String errorMessage;
    
    @OneToMany(mappedBy = "scanResult", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<SecretLeak> secrets;
    
    @OneToMany(mappedBy = "scanResult", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Vulnerability> vulnerabilities;
    
    @OneToMany(mappedBy = "scanResult", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<OutdatedDependency> outdatedDependencies;
    
    public enum ScanStatus {
        SUCCESS, FAILED, IN_PROGRESS
    }
}
