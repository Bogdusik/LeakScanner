package com.leakscanner.service;

import com.leakscanner.dto.RepositoryDTO;
import com.leakscanner.service.RepositoryFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

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
                    .onStatus(status -> status.is4xxClientError(), clientResponse -> {
                        log.warn("GitHub API client error {} for tree request: {}/{}", 
                                clientResponse.statusCode(), repositoryDTO.getOwner(), repositoryDTO.getName());
                        return clientResponse.bodyToMono(String.class)
                                .map(body -> {
                                    HttpStatus status = HttpStatus.resolve(clientResponse.statusCode().value());
                                    return new WebClientResponseException(
                                            clientResponse.statusCode().value(),
                                            status != null ? status.getReasonPhrase() : "Unknown",
                                            clientResponse.headers().asHttpHeaders(),
                                            body != null ? body.getBytes() : null,
                                            null
                                    );
                                });
                    })
                    .onStatus(status -> status.is5xxServerError(), clientResponse -> {
                        log.error("GitHub API server error {} for tree request: {}/{}", 
                                clientResponse.statusCode(), repositoryDTO.getOwner(), repositoryDTO.getName());
                        return clientResponse.bodyToMono(String.class)
                                .map(body -> {
                                    HttpStatus status = HttpStatus.resolve(clientResponse.statusCode().value());
                                    return new WebClientResponseException(
                                            clientResponse.statusCode().value(),
                                            status != null ? status.getReasonPhrase() : "Unknown",
                                            clientResponse.headers().asHttpHeaders(),
                                            body != null ? body.getBytes() : null,
                                            null
                                    );
                                });
                    })
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(20)) // Increased timeout
                    .block();
            
            if (response != null && response.containsKey("tree")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> tree = (List<Map<String, Object>>) response.get("tree");
                
                int maxFiles = 30; // Further reduced for faster scanning (30 files max)
                int fileCount = 0;
                
                for (Map<String, Object> item : tree) {
                    if (fileCount >= maxFiles) {
                        log.debug("Reached file limit ({}), stopping file collection", maxFiles);
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
                            if (content != null) {
                                files.add(new RepositoryFile(path, content));
                                fileCount++;
                            }
                        } catch (Exception e) {
                            log.warn("Error getting content for file {}: {} - skipping file", path, e.getMessage());
                            // Continue with next file instead of failing completely
                        }
                    }
                }
            }
            
        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.error("Repository not found on GitHub: {}/{} (404) - Check repository name and owner", 
                        repositoryDTO.getOwner(), repositoryDTO.getName());
            } else if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
                log.error("Access forbidden to repository on GitHub: {}/{} (403) - Repository may be private. Provide a valid GitHub token.", 
                        repositoryDTO.getOwner(), repositoryDTO.getName());
            } else if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                log.error("Unauthorized access to repository on GitHub: {}/{} (401) - GitHub token is invalid or expired", 
                        repositoryDTO.getOwner(), repositoryDTO.getName());
            } else {
                log.error("HTTP error getting repository files from GitHub: {}/{} - Status: {} {}", 
                        repositoryDTO.getOwner(), repositoryDTO.getName(), e.getStatusCode(), e.getMessage());
            }
        } catch (Exception e) {
            log.error("Error getting repository files from GitHub: {}/{} - {}", 
                    repositoryDTO.getOwner(), repositoryDTO.getName(), e.getMessage(), e);
        }
        
        return files;
    }
    
    public String getFileContent(RepositoryDTO repositoryDTO, String filePath, String token) {
        return getFileContent(repositoryDTO, filePath, token, null);
    }
    
    public String getFileContent(RepositoryDTO repositoryDTO, String filePath, String token, String ref) {
        try {
            WebClient webClient = createWebClient(token);
            
            // Build URI manually to avoid issues with path variables containing slashes
            // GitHub API expects path segments to be properly encoded
            // Encode each path segment separately, preserving slashes
            String[] pathSegments = filePath.split("/");
            StringBuilder encodedPath = new StringBuilder();
            for (int i = 0; i < pathSegments.length; i++) {
                if (i > 0) encodedPath.append("/");
                encodedPath.append(java.net.URLEncoder.encode(pathSegments[i], java.nio.charset.StandardCharsets.UTF_8)
                        .replace("+", "%20")); // GitHub expects %20 for spaces
            }
            
            // Build the full path manually
            String fullPath = String.format("/repos/%s/%s/contents/%s",
                    repositoryDTO.getOwner(),
                    repositoryDTO.getName(),
                    encodedPath.toString());
            
            // Add ref parameter if provided
            if (ref != null && !ref.isEmpty()) {
                fullPath += "?ref=" + java.net.URLEncoder.encode(ref, java.nio.charset.StandardCharsets.UTF_8);
            }
            
            var uriSpec = webClient.get().uri(fullPath);
            
            Map<String, Object> response = uriSpec
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError(), clientResponse -> {
                        log.debug("GitHub API client error {} for file {}: {}/{}", 
                                clientResponse.statusCode(), filePath, repositoryDTO.getOwner(), repositoryDTO.getName());
                        return clientResponse.bodyToMono(String.class)
                                .map(body -> {
                                    HttpStatus status = HttpStatus.resolve(clientResponse.statusCode().value());
                                    return new WebClientResponseException(
                                            clientResponse.statusCode().value(),
                                            status != null ? status.getReasonPhrase() : "Unknown",
                                            clientResponse.headers().asHttpHeaders(),
                                            body != null ? body.getBytes() : null,
                                            null
                                    );
                                });
                    })
                    .onStatus(status -> status.is5xxServerError(), clientResponse -> {
                        log.warn("GitHub API server error {} for file {}: {}/{}", 
                                clientResponse.statusCode(), filePath, repositoryDTO.getOwner(), repositoryDTO.getName());
                        return clientResponse.bodyToMono(String.class)
                                .map(body -> {
                                    HttpStatus status = HttpStatus.resolve(clientResponse.statusCode().value());
                                    return new WebClientResponseException(
                                            clientResponse.statusCode().value(),
                                            status != null ? status.getReasonPhrase() : "Unknown",
                                            clientResponse.headers().asHttpHeaders(),
                                            body != null ? body.getBytes() : null,
                                            null
                                    );
                                });
                    })
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(15)) // Increased timeout
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
            
        } catch (WebClientResponseException e) {
            // Handle specific HTTP errors
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.debug("File not found on GitHub: {} (404)", filePath);
            } else if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
                log.warn("Access forbidden to file on GitHub: {} (403) - Repository may be private or token invalid", filePath);
            } else if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                log.warn("Unauthorized access to file on GitHub: {} (401) - Token may be invalid or expired", filePath);
            } else {
                log.warn("HTTP error getting file content from GitHub for file {}: {} {}", 
                        filePath, e.getStatusCode(), e.getMessage());
            }
        } catch (java.lang.IllegalArgumentException e) {
            log.error("Illegal characters in file path {}: {}", filePath, e.getMessage());
        } catch (Exception e) {
            log.error("Error getting file content from GitHub for file {}: {}", filePath, e.getMessage());
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
                    .onStatus(status -> status.is4xxClientError(), clientResponse -> {
                        log.warn("GitHub API client error {} for repo info: {}/{} - Repository may not exist or be private", 
                                clientResponse.statusCode(), repositoryDTO.getOwner(), repositoryDTO.getName());
                        return clientResponse.bodyToMono(String.class)
                                .map(body -> {
                                    HttpStatus status = HttpStatus.resolve(clientResponse.statusCode().value());
                                    return new WebClientResponseException(
                                            clientResponse.statusCode().value(),
                                            status != null ? status.getReasonPhrase() : "Unknown",
                                            clientResponse.headers().asHttpHeaders(),
                                            body != null ? body.getBytes() : null,
                                            null
                                    );
                                });
                    })
                    .onStatus(status -> status.is5xxServerError(), clientResponse -> {
                        log.error("GitHub API server error {} for repo info: {}/{}", 
                                clientResponse.statusCode(), repositoryDTO.getOwner(), repositoryDTO.getName());
                        return clientResponse.bodyToMono(String.class)
                                .map(body -> {
                                    HttpStatus status = HttpStatus.resolve(clientResponse.statusCode().value());
                                    return new WebClientResponseException(
                                            clientResponse.statusCode().value(),
                                            status != null ? status.getReasonPhrase() : "Unknown",
                                            clientResponse.headers().asHttpHeaders(),
                                            body != null ? body.getBytes() : null,
                                            null
                                    );
                                });
                    })
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(15)) // Increased timeout
                    .block();
            
            if (repoInfo != null && repoInfo.containsKey("default_branch")) {
                return (String) repoInfo.get("default_branch");
            }
        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.debug("Repository not found on GitHub: {}/{} (404)", 
                        repositoryDTO.getOwner(), repositoryDTO.getName());
            } else if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
                log.warn("Access forbidden to repository on GitHub: {}/{} (403) - Repository may be private or token invalid", 
                        repositoryDTO.getOwner(), repositoryDTO.getName());
            } else if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                log.warn("Unauthorized access to repository on GitHub: {}/{} (401) - Token may be invalid or expired", 
                        repositoryDTO.getOwner(), repositoryDTO.getName());
            } else {
                log.debug("HTTP error getting default branch for {}/{}: {} {}", 
                        repositoryDTO.getOwner(), repositoryDTO.getName(), e.getStatusCode(), e.getMessage());
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
                    .onStatus(status -> status.is4xxClientError(), clientResponse -> {
                        // 404 is expected for non-existent branches, so we don't log it as error
                        if (clientResponse.statusCode() == HttpStatus.NOT_FOUND) {
                            return clientResponse.bodyToMono(String.class)
                                    .map(body -> {
                                        HttpStatus status = HttpStatus.resolve(clientResponse.statusCode().value());
                                        return new WebClientResponseException(
                                                clientResponse.statusCode().value(),
                                                status != null ? status.getReasonPhrase() : "Unknown",
                                                clientResponse.headers().asHttpHeaders(),
                                                body != null ? body.getBytes() : null,
                                                null
                                        );
                                    });
                        }
                        log.debug("GitHub API client error {} for branch {}: {}/{}", 
                                clientResponse.statusCode(), branchName, repositoryDTO.getOwner(), repositoryDTO.getName());
                        return clientResponse.bodyToMono(String.class)
                                .map(body -> {
                                    HttpStatus status = HttpStatus.resolve(clientResponse.statusCode().value());
                                    return new WebClientResponseException(
                                            clientResponse.statusCode().value(),
                                            status != null ? status.getReasonPhrase() : "Unknown",
                                            clientResponse.headers().asHttpHeaders(),
                                            body != null ? body.getBytes() : null,
                                            null
                                    );
                                });
                    })
                    .onStatus(status -> status.is5xxServerError(), clientResponse -> {
                        log.warn("GitHub API server error {} for branch {}: {}/{}", 
                                clientResponse.statusCode(), branchName, repositoryDTO.getOwner(), repositoryDTO.getName());
                        return clientResponse.bodyToMono(String.class)
                                .map(body -> {
                                    HttpStatus status = HttpStatus.resolve(clientResponse.statusCode().value());
                                    return new WebClientResponseException(
                                            clientResponse.statusCode().value(),
                                            status != null ? status.getReasonPhrase() : "Unknown",
                                            clientResponse.headers().asHttpHeaders(),
                                            body != null ? body.getBytes() : null,
                                            null
                                    );
                                });
                    })
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10)) // Increased timeout
                    .block();
            
            if (response != null && response.containsKey("tree")) {
                log.info("Branch '{}' exists for {}/{}", branchName, repositoryDTO.getOwner(), repositoryDTO.getName());
                return branchName;
            }
        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                // 404 is expected for non-existent branches, so we don't log it
                log.debug("Branch '{}' does not exist for {}/{}", branchName, repositoryDTO.getOwner(), repositoryDTO.getName());
            } else {
                log.debug("HTTP error checking branch '{}' for {}/{}: {} {}", 
                        branchName, repositoryDTO.getOwner(), repositoryDTO.getName(), e.getStatusCode(), e.getMessage());
            }
        } catch (Exception e) {
            log.debug("Error checking branch '{}' for {}/{}: {}", 
                    branchName, repositoryDTO.getOwner(), repositoryDTO.getName(), e.getMessage());
        }
        return null;
    }
    
    private WebClient createWebClient(String token) {
        WebClient.Builder builder = webClientBuilder.baseUrl(githubBaseUrl);
        
        if (token != null && !token.isEmpty()) {
            // GitHub API v3 uses "token" prefix, v4 uses "Bearer"
            builder.defaultHeader("Authorization", "token " + token);
        } else {
            // Add User-Agent header (GitHub API requires it)
            builder.defaultHeader("User-Agent", "LeakScanner/1.0");
        }
        
        return builder.build();
    }
}
