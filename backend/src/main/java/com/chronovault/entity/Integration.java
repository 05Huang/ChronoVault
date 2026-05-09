package com.chronovault.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "integrations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Integration {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IntegrationType type;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String url;

    @Column(nullable = false)
    private Boolean active;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (active == null) active = true;
    }

    public enum IntegrationType { SLACK, EMAIL, WEBHOOK, DINGTALK }
}
