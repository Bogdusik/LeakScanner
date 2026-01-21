package com.leakscanner.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OutdatedDependencyDTO {
    private String name;
    private String currentVersion;
    private String latestVersion;
    private String type;
}
