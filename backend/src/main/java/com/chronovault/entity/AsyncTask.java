package com.chronovault.entity;

import com.chronovault.task.TaskType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

@Entity
@Table(name = "async_tasks")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AsyncTask {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_id")
    private Server server;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TaskType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status;

    @Column
    private Integer progress;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String result;

    @Column(columnDefinition = "TEXT")
    private String error;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = TaskStatus.PENDING;
        if (progress == null) progress = 0;
    }

    public enum TaskStatus { PENDING, RUNNING, COMPLETED, FAILED, CANCELLED }
}
