package com.leakscanner.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "outdated_dependencies")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OutdatedDependency {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scan_result_id", nullable = false)
    private ScanResult scanResult;
    
    @Column(nullable = false, length = 200)
    private String name;
    
    @Column(name = "current_version", nullable = false, length = 50)
    private String currentVersion;
    
    @Column(name = "latest_version", nullable = false, length = 50)
    private String latestVersion;
    
    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private DependencyType type;
    
    public enum DependencyType {
        NPM, MAVEN, GRADLE, PYTHON, PIP, OTHER
    }
}
