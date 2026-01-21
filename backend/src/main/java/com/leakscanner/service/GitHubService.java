package com.leakscanner.service;

import com.leakscanner.dto.RepositoryDTO;
import com.leakscanner.service.RepositoryFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubService {
    
    private final WebClient.Builder webClientBuilder;
    
    @Value("${leakscanner.api.github.base-url:https://api.github.com}")
    private String githubBaseUrl;
    
    @Cacheable(value = "repositoryFiles", key = "#repositoryDTO.platform + ':' + #repositoryDTO.owner + '/' + #repositoryDTO.name")
    public List<RepositoryFile> getRepositoryFiles(RepositoryDTO repositoryDTO, String token) {
        List<RepositoryFile> files = new ArrayList<>();
        
        try {
            WebClient webClient = createWebClient(token);
            
            // Get repository tree
            String url = String.format("%s/repos/%s/%s/git/trees/main?recursive=1", 
                    githubBaseUrl, repositoryDTO.getOwner(), repositoryDTO.getName());
            
            Map<String, Object> response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            
            if (response != null && response.containsKey("tree")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> tree = (List<Map<String, Object>>) response.get("tree");
                
                for (Map<String, Object> item : tree) {
                    String path = (String) item.get("path");
                    String type = (String) item.get("type");
                    
                    if ("blob".equals(type)) {
                        String content = getFileContent(repositoryDTO, path, token);
                        files.add(new RepositoryFile(path, content));
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("Error getting repository files from GitHub", e);
        }
        
        return files;
    }
    
    public String getFileContent(RepositoryDTO repositoryDTO, String filePath, String token) {
        try {
            WebClient webClient = createWebClient(token);
            
            String url = String.format("%s/repos/%s/%s/contents/%s", 
                    githubBaseUrl, repositoryDTO.getOwner(), repositoryDTO.getName(), filePath);
            
            Map<String, Object> response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            
            if (response != null && response.containsKey("content")) {
                String encodedContent = (String) response.get("content");
                // Base64 decode
                return new String(java.util.Base64.getDecoder().decode(encodedContent));
            }
            
        } catch (Exception e) {
            log.error("Error getting file content from GitHub", e);
        }
        
        return null;
    }
    
    private WebClient createWebClient(String token) {
        WebClient.Builder builder = webClientBuilder.baseUrl(githubBaseUrl);
        
        if (token != null && !token.isEmpty()) {
            builder.defaultHeader("Authorization", "token " + token);
        }
        
        return builder.build();
    }
}
