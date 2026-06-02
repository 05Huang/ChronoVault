package com.chronovault.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "container_states")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ContainerState {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private Snapshot snapshot;

    @Column(name = "container_name", nullable = false, length = 200)
    private String containerName;

    @Column(length = 200)
    private String image;

    @Column(length = 50)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String ports;

    @Column(columnDefinition = "TEXT")
    private String volumes;

    @Column(columnDefinition = "TEXT")
    private String networks;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}