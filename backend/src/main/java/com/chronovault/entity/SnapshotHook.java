package com.chronovault.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "snapshot_hooks")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SnapshotHook {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_id", nullable = false)
    private Server server;

    @Enumerated(EnumType.STRING)
    @Column(name = "hook_type", nullable = false, length = 30)
    private HookType hookType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String command;

    @Column(name = "timeout_seconds")
    @Builder.Default
    private Integer timeoutSeconds = 60;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "order_index")
    @Builder.Default
    private Integer orderIndex = 0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum HookType {
        PRE_SNAPSHOT, POST_SNAPSHOT, PRE_RESTORE, POST_RESTORE
    }
}