package com.chronovault.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "servers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Server {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 45)
    private String ip;

    @Column(length = 100)
    private String os;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ServerStatus status;

    @Column(name = "uptime_seconds")
    private Long uptimeSeconds;

    @Column(name = "ssh_port")
    @Builder.Default
    private Integer sshPort = 22;

    @Column(name = "ssh_username", length = 100)
    @Builder.Default
    private String sshUsername = "root";

    @Column(name = "ssh_key_encrypted", columnDefinition = "TEXT")
    private String sshKeyEncrypted;

    @Column(name = "ssh_auth_method", length = 20)
    @Builder.Default
    private String sshAuthMethod = "KEY";

    @Column(name = "agent_id", length = 100)
    private String agentId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (status == null) status = ServerStatus.RUNNING;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum ServerStatus { RUNNING, STOPPED, ERROR }
}
