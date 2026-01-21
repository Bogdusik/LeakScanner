package com.leakscanner.service;

import com.leakscanner.model.OutdatedDependency;
import com.leakscanner.model.SecretLeak;
import com.leakscanner.model.Vulnerability;
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
public class SecurityScanResult {
    @Builder.Default
    private List<SecretLeak> secrets = new ArrayList<>();
    
    @Builder.Default
    private List<Vulnerability> vulnerabilities = new ArrayList<>();
    
    @Builder.Default
    private List<OutdatedDependency> outdatedDependencies = new ArrayList<>();
}
