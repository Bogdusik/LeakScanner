package com.leakscanner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RepositoryDTO {
    @NotBlank(message = "Owner is required")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Owner contains invalid characters")
    private String owner;
    
    @NotBlank(message = "Repository name is required")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Repository name contains invalid characters")
    private String name;
    
    @NotBlank(message = "Platform is required")
    @Pattern(regexp = "^(github|gitlab)$", message = "Platform must be 'github' or 'gitlab'")
    private String platform;
}
