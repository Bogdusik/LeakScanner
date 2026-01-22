package com.leakscanner.service;

import com.leakscanner.dto.RepositoryDTO;
import com.leakscanner.service.RepositoryFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitLabService {
    
    private final WebClient.Builder webClientBuilder;
    
    @Value("${leakscanner.api.gitlab.base-url:https://gitlab.com/api/v4}")
    private String gitlabBaseUrl;
    
    public List<RepositoryFile> getRepositoryFiles(RepositoryDTO repositoryDTO, String token) {
        List<RepositoryFile> files = new ArrayList<>();
        
        try {
            WebClient webClient = createWebClient(token);
            
            // Get project ID first
            String projectId = getProjectId(repositoryDTO, token);
            if (projectId == null) return files;
            
            // Get repository tree
            String url = String.format("%s/projects/%s/repository/tree?recursive=true", 
                    gitlabBaseUrl, projectId);
            
            List<Map<String, Object>> response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToFlux(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                    .collectList()
                    .timeout(Duration.ofSeconds(15))
                    .block();
            
            if (response != null) {
                int maxFiles = 50; // Reduced to 50 files to prevent hanging
                int fileCount = 0;
                
                for (Map<String, Object> item : response) {
                    if (fileCount >= maxFiles) {
                        log.warn("Reached file limit ({}), stopping file collection", maxFiles);
                        break;
                    }
                    
                    String path = (String) item.get("path");
                    String type = (String) item.get("type");
                    
                    if ("blob".equals(type)) {
                        try {
                            String content = getFileContent(repositoryDTO, path, token);
                            files.add(new RepositoryFile(path, content));
                            fileCount++;
                        } catch (Exception e) {
                            log.warn("Error getting content for file {}: {}", path, e.getMessage());
                            // Continue with next file instead of failing completely
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("Error getting repository files from GitLab", e);
        }
        
        return files;
    }
    
    public String getFileContent(RepositoryDTO repositoryDTO, String filePath, String token) {
        try {
            WebClient webClient = createWebClient(token);
            
            String projectId = getProjectId(repositoryDTO, token);
            if (projectId == null) return null;
            
            String url = String.format("%s/projects/%s/repository/files/%s/raw", 
                    gitlabBaseUrl, projectId, java.net.URLEncoder.encode(filePath, java.nio.charset.StandardCharsets.UTF_8));
            
            return webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            
        } catch (Exception e) {
            log.error("Error getting file content from GitLab", e);
        }
        
        return null;
    }
    
    private String getProjectId(RepositoryDTO repositoryDTO, String token) {
        try {
            WebClient webClient = createWebClient(token);
            
            String url = String.format("%s/projects/%s%%2F%s", 
                    gitlabBaseUrl, repositoryDTO.getOwner(), repositoryDTO.getName());
            
            Map<String, Object> response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            
            if (response != null && response.containsKey("id")) {
                return String.valueOf(response.get("id"));
            }
            
        } catch (Exception e) {
            log.error("Error getting project ID from GitLab", e);
        }
        
        return null;
    }
    
    private WebClient createWebClient(String token) {
        WebClient.Builder builder = webClientBuilder.baseUrl(gitlabBaseUrl);
        
        if (token != null && !token.isEmpty()) {
            builder.defaultHeader("PRIVATE-TOKEN", token);
        }
        
        return builder.build();
    }
}
