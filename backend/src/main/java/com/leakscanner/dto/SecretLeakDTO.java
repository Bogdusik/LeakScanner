package com.leakscanner.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SecretLeakDTO {
    private String type;
    private String file;
    private Integer line;
    private String severity;
    private String pattern;
}
