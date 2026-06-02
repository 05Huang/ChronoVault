package com.chronovault.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "server_groups")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ServerGroup {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "environment_type", nullable = false, length = 30)
    @Builder.Default
    private EnvironmentType environmentType = EnvironmentType.DEVELOPMENT;

    @Column(length = 20)
    @Builder.Default
    private String color = "#0058BE";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum EnvironmentType {
        PRODUCTION, STAGING, DEVELOPMENT, TESTING
    }
}