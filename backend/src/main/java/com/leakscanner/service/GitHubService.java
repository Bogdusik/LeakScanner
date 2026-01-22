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
public class GitHubService {
    
    private final WebClient.Builder webClientBuilder;
    
    @Value("${leakscanner.api.github.base-url:https://api.github.com}")
    private String githubBaseUrl;
    
    // Removed @Cacheable to prevent hanging on cached slow operations
    public List<RepositoryFile> getRepositoryFiles(RepositoryDTO repositoryDTO, String token) {
        List<RepositoryFile> files = new ArrayList<>();
        
        try {
            WebClient webClient = createWebClient(token);
            
            // First, get repository info to determine default branch
            String defaultBranch = getDefaultBranch(repositoryDTO, token, webClient);
            if (defaultBranch == null) {
                log.warn("Could not determine default branch for {}/{}, trying 'main' and 'master'", 
                        repositoryDTO.getOwner(), repositoryDTO.getName());
                // Try main first, then master
                defaultBranch = tryBranch(repositoryDTO, token, webClient, "main");
                if (defaultBranch == null) {
                    defaultBranch = tryBranch(repositoryDTO, token, webClient, "master");
                }
                if (defaultBranch == null) {
                    log.error("Could not find valid branch for {}/{}", 
                            repositoryDTO.getOwner(), repositoryDTO.getName());
                    return files;
                }
            }
            
            log.info("Using branch '{}' for {}/{}", defaultBranch, repositoryDTO.getOwner(), repositoryDTO.getName());
            
            // Get repository tree using the correct branch
            String url = String.format("%s/repos/%s/%s/git/trees/%s?recursive=1", 
                    githubBaseUrl, repositoryDTO.getOwner(), repositoryDTO.getName(), defaultBranch);
            
            Map<String, Object> response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();
            
            if (response != null && response.containsKey("tree")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> tree = (List<Map<String, Object>>) response.get("tree");
                
                int maxFiles = 50; // Reduced to 50 files to prevent hanging
                int fileCount = 0;
                
                for (Map<String, Object> item : tree) {
                    if (fileCount >= maxFiles) {
                        log.warn("Reached file limit ({}), stopping file collection", maxFiles);
                        break;
                    }
                    
                    String path = (String) item.get("path");
                    String type = (String) item.get("type");
                    
                    if ("blob".equals(type)) {
                        // Check file size before loading (GitHub API provides size in bytes)
                        Object sizeObj = item.get("size");
                        if (sizeObj instanceof Number) {
                            long fileSize = ((Number) sizeObj).longValue();
                            long maxFileSize = 10 * 1024 * 1024; // 10MB default
                            if (fileSize > maxFileSize) {
                                log.debug("Skipping large file: {} ({} bytes)", path, fileSize);
                                continue;
                            }
                        }
                        
                        try {
                            String content = getFileContent(repositoryDTO, path, token, defaultBranch);
                            if (content != null && content.length() > 10 * 1024 * 1024) {
                                log.debug("Skipping large file content: {} ({} bytes)", path, content.length());
                                continue;
                            }
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
            log.error("Error getting repository files from GitHub", e);
        }
        
        return files;
    }
    
    public String getFileContent(RepositoryDTO repositoryDTO, String filePath, String token) {
        return getFileContent(repositoryDTO, filePath, token, null);
    }
    
    public String getFileContent(RepositoryDTO repositoryDTO, String filePath, String token, String ref) {
        try {
            WebClient webClient = createWebClient(token);
            
            String url = String.format("%s/repos/%s/%s/contents/%s", 
                    githubBaseUrl, repositoryDTO.getOwner(), repositoryDTO.getName(), filePath);
            if (ref != null && !ref.isEmpty()) {
                url += "?ref=" + ref;
            }
            
            Map<String, Object> response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            
            if (response != null && response.containsKey("content")) {
                Object contentObj = response.get("content");
                if (contentObj == null) {
                    return null;
                }
                
                String encodedContent = contentObj.toString();
                if (encodedContent == null || encodedContent.isEmpty()) {
                    return null;
                }
                
                // Aggressively clean base64 string (remove ALL whitespace, newlines, and invalid characters)
                encodedContent = encodedContent.replaceAll("[^A-Za-z0-9+/=]", "");
                
                // Validate base64 length (must be multiple of 4)
                int padding = encodedContent.length() % 4;
                if (padding > 0) {
                    encodedContent += "====".substring(0, 4 - padding);
                }
                
                try {
                    // Base64 decode
                    byte[] decodedBytes = java.util.Base64.getDecoder().decode(encodedContent);
                    return new String(decodedBytes, java.nio.charset.StandardCharsets.UTF_8);
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid base64 content for file {} (length: {}), skipping: {}", 
                            filePath, encodedContent.length(), e.getMessage());
                    return null;
                } catch (Exception e) {
                    log.warn("Error decoding base64 for file {}: {}", filePath, e.getMessage());
                    return null;
                }
            }
            
        } catch (Exception e) {
            log.error("Error getting file content from GitHub", e);
        }
        
        return null;
    }
    
    private String getDefaultBranch(RepositoryDTO repositoryDTO, String token, WebClient webClient) {
        try {
            String url = String.format("%s/repos/%s/%s", 
                    githubBaseUrl, repositoryDTO.getOwner(), repositoryDTO.getName());
            
            Map<String, Object> repoInfo = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            
            if (repoInfo != null && repoInfo.containsKey("default_branch")) {
                return (String) repoInfo.get("default_branch");
            }
        } catch (Exception e) {
            log.debug("Error getting default branch for {}/{}: {}", 
                    repositoryDTO.getOwner(), repositoryDTO.getName(), e.getMessage());
        }
        return null;
    }
    
    private String tryBranch(RepositoryDTO repositoryDTO, String token, WebClient webClient, String branchName) {
        try {
            String url = String.format("%s/repos/%s/%s/git/trees/%s?recursive=1", 
                    githubBaseUrl, repositoryDTO.getOwner(), repositoryDTO.getName(), branchName);
            
            Map<String, Object> response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
            
            if (response != null && response.containsKey("tree")) {
                log.info("Branch '{}' exists for {}/{}", branchName, repositoryDTO.getOwner(), repositoryDTO.getName());
                return branchName;
            }
        } catch (Exception e) {
            log.debug("Branch '{}' does not exist for {}/{}", branchName, repositoryDTO.getOwner(), repositoryDTO.getName());
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
