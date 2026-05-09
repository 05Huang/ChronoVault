package com.chronovault.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "risks")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Risk {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_id")
    private Server server;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RiskLevel level;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String category;

    @Column(length = 100)
    private String source;

    @Column(name = "ai_suggestion", columnDefinition = "TEXT")
    private String aiSuggestion;

    @Column(name = "action_text", length = 100)
    private String actionText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RiskStatus status;

    @Column(name = "discovered_at")
    private LocalDateTime discoveredAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        discoveredAt = createdAt;
        if (status == null) status = RiskStatus.OPEN;
    }

    public enum RiskLevel { CRITICAL, WARNING, ANOMALOUS }
    public enum RiskStatus { OPEN, MITIGATED }
}
