package com.leakscanner.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "repositories", indexes = {
    @Index(name = "idx_platform_owner_name", columnList = "platform,owner,name")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Repository {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String platform;
    
    @Column(nullable = false)
    private String owner;
    
    @Column(nullable = false)
    private String name;
    
    @Column(name = "full_name", nullable = false, unique = true)
    private String fullName;
    
    @Column(name = "is_private")
    private Boolean isPrivate;
    
    @Column(name = "default_branch")
    private String defaultBranch;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
