package com.chronovault.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "containers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Container {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_id", nullable = false)
    private Server server;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContainerType type;

    @Column(name = "cpu_percent")
    private Double cpuPercent;

    @Column(name = "memory_percent")
    private Double memoryPercent;

    @Column(name = "memory_mb")
    private Long memoryMb;

    @Column(name = "disk_io")
    private String diskIo;

    @Column(length = 500)
    private String networks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContainerStatus status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = ContainerStatus.RUNNING;
    }

    public enum ContainerType { HTTP, DATABASE, CACHE, NGINX, MYSQL, REDIS, API }
    public enum ContainerStatus { RUNNING, STOPPED, ERROR, PAUSED }
}
