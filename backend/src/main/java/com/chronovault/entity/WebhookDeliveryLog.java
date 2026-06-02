package com.chronovault.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "webhook_delivery_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WebhookDeliveryLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "webhook_id", nullable = false)
    private WebhookEndpoint webhook;

    @Column(nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "response_code")
    private Integer responseCode;

    @Column(columnDefinition = "TEXT")
    private String error;

    @Column(name = "attempt")
    @Builder.Default
    private Integer attempt = 1;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}