package com.leakscanner.service;

import com.leakscanner.dto.RepositoryDTO;
import com.leakscanner.model.OutdatedDependency;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DependencyScannerService {
    
    private final WebClient.Builder webClientBuilder;
    private final GitHubService githubService;
    private final GitLabService gitLabService;
    private final NpmRegistryService npmRegistryService;
    
    public List<OutdatedDependency> scanForOutdatedDependencies(
            RepositoryDTO repositoryDTO,
            String githubToken,
            String gitlabToken
    ) {
        log.info("Scanning for outdated dependencies in {}: {}/{}", 
                repositoryDTO.getPlatform(), repositoryDTO.getOwner(), repositoryDTO.getName());
        
        List<OutdatedDependency> outdatedDependencies = new ArrayList<>();
        
        try {
            // Get package.json
            String packageJson = getPackageJson(repositoryDTO, githubToken, gitlabToken);
            
            if (packageJson != null) {
                outdatedDependencies.addAll(npmRegistryService.checkOutdatedDependencies(packageJson));
            }
            
        } catch (Exception e) {
            log.error("Error scanning for outdated dependencies", e);
        }
        
        return outdatedDependencies;
    }
    
    private String getPackageJson(RepositoryDTO repositoryDTO, String githubToken, String gitlabToken) {
        if ("github".equalsIgnoreCase(repositoryDTO.getPlatform())) {
            return githubService.getFileContent(repositoryDTO, "package.json", githubToken);
        } else {
            return gitLabService.getFileContent(repositoryDTO, "package.json", gitlabToken);
        }
    }
}
