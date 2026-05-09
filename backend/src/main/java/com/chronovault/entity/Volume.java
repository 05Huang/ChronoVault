package com.chronovault.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "volumes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Volume {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_id", nullable = false)
    private Server server;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "container_path", length = 500)
    private String containerPath;

    @Column(name = "host_path", length = 500)
    private String hostPath;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(length = 50)
    private String status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
