package com.leakscanner.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "secret_leaks")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SecretLeak {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scan_result_id", nullable = false)
    private ScanResult scanResult;
    
    @Column(nullable = false)
    private String type;
    
    @Column(nullable = false)
    private String file;
    
    @Column(nullable = false)
    private Integer line;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Severity severity;
    
    @Column(length = 500)
    private String pattern;
    
    public enum Severity {
        CRITICAL, HIGH, MEDIUM, LOW
    }
}
