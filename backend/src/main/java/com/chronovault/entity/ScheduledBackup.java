package com.chronovault.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "scheduled_backups")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ScheduledBackup {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_id", nullable = false)
    private Server server;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "storage_target_id")
    private StorageTarget storageTarget;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "cron_expression", nullable = false, length = 50)
    private String cronExpression;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(columnDefinition = "TEXT")
    private String paths;

    @Column(columnDefinition = "TEXT")
    private String excludes;

    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    @Column(name = "next_run_at")
    private LocalDateTime nextRunAt;

    @Column(name = "last_status", length = 20)
    @Enumerated(EnumType.STRING)
    private RunStatus lastStatus;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "run_count", nullable = false)
    @Builder.Default
    private Integer runCount = 0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum RunStatus {
        SUCCESS, FAILED, RUNNING
    }
}
