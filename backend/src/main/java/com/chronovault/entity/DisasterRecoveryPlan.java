package com.chronovault.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "disaster_recovery_plans")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DisasterRecoveryPlan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String steps;

    @Column(name = "estimated_rto")
    private Integer estimatedRto;

    @Column(name = "estimated_rpo")
    private Integer estimatedRpo;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private PlanStatus status = PlanStatus.DRAFT;

    @Column(name = "last_executed_at")
    private LocalDateTime lastExecutedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum PlanStatus {
        DRAFT, ACTIVE, ARCHIVED
    }
}