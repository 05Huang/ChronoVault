package com.chronovault.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "verification_jobs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VerificationJob {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_id", nullable = false)
    private Server server;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "storage_target_id")
    private StorageTarget storageTarget;

    @Column(name = "schedule_cron", length = 50)
    @Builder.Default
    private String scheduleCron = "0 * * * *";

    @Column(name = "last_status", length = 20)
    @Builder.Default
    private String lastStatus = "PENDING";

    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}