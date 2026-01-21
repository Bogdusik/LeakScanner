package com.leakscanner.service;

import com.leakscanner.model.OutdatedDependency;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NpmRegistryService {
    
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    
    @Value("${leakscanner.api.npm.registry-url:https://registry.npmjs.org}")
    private String npmRegistryUrl;
    
    public List<OutdatedDependency> checkOutdatedDependencies(String packageJsonContent) {
        List<OutdatedDependency> outdatedDependencies = new ArrayList<>();
        
        try {
            JsonNode packageJson = objectMapper.readTree(packageJsonContent);
            JsonNode dependencies = packageJson.get("dependencies");
            
            if (dependencies != null) {
                Iterator<Map.Entry<String, JsonNode>> fields = dependencies.fields();
                
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    String packageName = entry.getKey();
                    String currentVersion = entry.getValue().asText().replace("^", "").replace("~", "");
                    
                    String latestVersion = getLatestVersion(packageName);
                    
                    if (latestVersion != null && !currentVersion.equals(latestVersion)) {
                        OutdatedDependency dep = new OutdatedDependency();
                        dep.setName(packageName);
                        dep.setCurrentVersion(currentVersion);
                        dep.setLatestVersion(latestVersion);
                        dep.setType(OutdatedDependency.DependencyType.NPM);
                        outdatedDependencies.add(dep);
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("Error checking outdated dependencies", e);
        }
        
        return outdatedDependencies;
    }
    
    private String getLatestVersion(String packageName) {
        try {
            WebClient webClient = webClientBuilder.baseUrl(npmRegistryUrl).build();
            
            Map<String, Object> response = webClient.get()
                    .uri("/" + packageName)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            
            if (response != null && response.containsKey("dist-tags")) {
                @SuppressWarnings("unchecked")
                Map<String, String> distTags = (Map<String, String>) response.get("dist-tags");
                return distTags.get("latest");
            }
            
        } catch (Exception e) {
            log.error("Error getting latest version for package: {}", packageName, e);
        }
        
        return null;
    }
}
