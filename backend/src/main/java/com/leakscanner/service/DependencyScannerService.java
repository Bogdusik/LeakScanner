package com.leakscanner.service;

import com.leakscanner.dto.RepositoryDTO;
import com.leakscanner.model.OutdatedDependency;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
            // Scan npm/Node.js dependencies
            String packageJson = getPackageFile(repositoryDTO, "package.json", githubToken, gitlabToken);
            if (packageJson != null) {
                outdatedDependencies.addAll(npmRegistryService.checkOutdatedDependencies(packageJson));
            }
            
            // Scan Maven/Java dependencies
            String pomXml = getPackageFile(repositoryDTO, "pom.xml", githubToken, gitlabToken);
            if (pomXml != null) {
                outdatedDependencies.addAll(checkMavenDependencies(pomXml));
            }
            
            // Scan Python dependencies
            String requirementsTxt = getPackageFile(repositoryDTO, "requirements.txt", githubToken, gitlabToken);
            if (requirementsTxt != null) {
                outdatedDependencies.addAll(checkPythonDependencies(requirementsTxt));
            }
            
        } catch (Exception e) {
            log.error("Error scanning for outdated dependencies", e);
        }
        
        return outdatedDependencies;
    }
    
    private String getPackageFile(RepositoryDTO repositoryDTO, String fileName, String githubToken, String gitlabToken) {
        try {
            // Add timeout protection for file retrieval
            if ("github".equalsIgnoreCase(repositoryDTO.getPlatform())) {
                return githubService.getFileContent(repositoryDTO, fileName, githubToken);
            } else {
                return gitLabService.getFileContent(repositoryDTO, fileName, gitlabToken);
            }
        } catch (Exception e) {
            log.debug("{} not found or error reading it: {}", fileName, e.getMessage());
            return null;
        }
    }
    
    private List<OutdatedDependency> checkMavenDependencies(String pomXml) {
        List<OutdatedDependency> outdatedDependencies = new ArrayList<>();
        
        try {
            // Parse pom.xml and check Maven Central for latest versions
            // This is a simplified version - full implementation would use Maven Central API
            log.info("Checking Maven dependencies for outdated packages");
            
            // Extract dependencies from pom.xml (simplified regex-based approach)
            java.util.regex.Pattern dependencyPattern = java.util.regex.Pattern.compile(
                    "<dependency>.*?<groupId>(.*?)</groupId>.*?<artifactId>(.*?)</artifactId>.*?<version>(.*?)</version>.*?</dependency>",
                    java.util.regex.Pattern.DOTALL
            );
            
            java.util.regex.Matcher matcher = dependencyPattern.matcher(pomXml);
            while (matcher.find()) {
                String groupId = matcher.group(1).trim();
                String artifactId = matcher.group(2).trim();
                String currentVersion = matcher.group(3).trim();
                
                // Check Maven Central for latest version
                String latestVersion = getMavenLatestVersion(groupId, artifactId);
                
                if (latestVersion != null && !currentVersion.equals(latestVersion)) {
                    OutdatedDependency dep = new OutdatedDependency();
                    dep.setName(groupId + ":" + artifactId);
                    dep.setCurrentVersion(currentVersion);
                    dep.setLatestVersion(latestVersion);
                    dep.setType(OutdatedDependency.DependencyType.MAVEN);
                    outdatedDependencies.add(dep);
                }
            }
            
        } catch (Exception e) {
            log.error("Error checking Maven dependencies", e);
        }
        
        return outdatedDependencies;
    }
    
    private String getMavenLatestVersion(String groupId, String artifactId) {
        try {
            WebClient webClient = webClientBuilder.baseUrl("https://search.maven.org/solrsearch/select").build();
            
            String query = String.format("g:\"%s\" AND a:\"%s\"", groupId, artifactId);
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("q", query)
                            .queryParam("rows", "1")
                            .queryParam("wt", "json")
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            
            if (response != null && response.containsKey("response")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseData = (Map<String, Object>) response.get("response");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> docs = (List<Map<String, Object>>) responseData.get("docs");
                
                if (docs != null && !docs.isEmpty()) {
                    return (String) docs.get(0).get("latestVersion");
                }
            }
            
        } catch (Exception e) {
            log.debug("Error getting Maven latest version for {}:{}", groupId, artifactId, e);
        }
        
        return null;
    }
    
    private List<OutdatedDependency> checkPythonDependencies(String requirementsTxt) {
        List<OutdatedDependency> outdatedDependencies = new ArrayList<>();
        
        try {
            // Parse requirements.txt
            String[] lines = requirementsTxt.split("\n");
            
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                // Parse package name and version (format: package==version or package>=version, etc.)
                String[] parts = line.split("[=<>!]");
                if (parts.length >= 1) {
                    String packageName = parts[0].trim();
                    String currentVersion = parts.length >= 2 ? parts[parts.length - 1].trim() : "unknown";
                    
                    // Check PyPI for latest version
                    String latestVersion = getPyPILatestVersion(packageName);
                    
                    if (latestVersion != null && !currentVersion.equals(latestVersion)) {
                        OutdatedDependency dep = new OutdatedDependency();
                        dep.setName(packageName);
                        dep.setCurrentVersion(currentVersion);
                        dep.setLatestVersion(latestVersion);
                        dep.setType(OutdatedDependency.DependencyType.PYTHON);
                        outdatedDependencies.add(dep);
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("Error checking Python dependencies", e);
        }
        
        return outdatedDependencies;
    }
    
    private String getPyPILatestVersion(String packageName) {
        try {
            WebClient webClient = webClientBuilder.baseUrl("https://pypi.org/pypi").build();
            
            Map<String, Object> response = webClient.get()
                    .uri("/" + packageName + "/json")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            
            if (response != null && response.containsKey("info")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> info = (Map<String, Object>) response.get("info");
                return (String) info.get("version");
            }
            
        } catch (Exception e) {
            log.debug("Error getting PyPI latest version for {}", packageName, e);
        }
        
        return null;
    }
}
