package com.chronovault.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "snapshots")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Snapshot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_id", nullable = false)
    private Server server;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SnapshotStatus status;

    @Column(length = 64)
    private String hash;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SnapshotType type;

    @Column(length = 500)
    private String note;

    @Column(name = "microservice_count")
    private Integer microserviceCount;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = SnapshotStatus.STABLE;
        if (type == null) type = SnapshotType.FULL;
    }

    public enum SnapshotStatus { STABLE, WARNING, ARCHIVED }
    public enum SnapshotType { FULL, INCREMENTAL }
}
