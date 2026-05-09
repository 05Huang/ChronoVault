package com.chronovault.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

@Entity
@Table(name = "storage_targets")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StorageTarget {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StorageType type;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String endpoint;

    @Column(name = "used_bytes")
    private Long usedBytes;

    @Column(name = "total_bytes")
    private Long totalBytes;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private StorageStatus status;

    @Column(name = "credentials_encrypted", columnDefinition = "TEXT")
    private String credentialsEncrypted;

    @Column(columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String config;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = StorageStatus.ACTIVE;
    }

    public enum StorageType { LOCAL, S3, OSS, WEBDAV, BLOCK, ARCHIVE }
    public enum StorageStatus { ACTIVE, INACTIVE, ERROR }
}
