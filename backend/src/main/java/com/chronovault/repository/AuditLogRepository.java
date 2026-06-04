package com.chronovault.repository;

import com.chronovault.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findAllByOrderByCreatedAtDesc();

    /** Paged query to prevent OOM */
    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Paged query for audit logs within a time range — replaces full-table scans */
    Page<AuditLog> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);

    List<AuditLog> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:action IS NULL OR a.action LIKE %:action%) AND " +
           "(:userId IS NULL OR a.user.id = :userId) AND " +
           "(:since IS NULL OR a.createdAt >= :since) AND " +
           "(:until IS NULL OR a.createdAt <= :until) " +
           "ORDER BY a.createdAt DESC")
    Page<AuditLog> search(@Param("action") String action,
                          @Param("userId") Long userId,
                          @Param("since") LocalDateTime since,
                          @Param("until") LocalDateTime until,
                          Pageable pageable);

    /**
     * Find audit logs for a specific resource — view operation history for any resource.
     */
    List<AuditLog> findByResourceTypeAndResourceIdOrderByCreatedAtDesc(String resourceType, Long resourceId);

    /**
     * Delete audit logs older than a given date — for retention enforcement.
     */
    void deleteByCreatedAtBefore(LocalDateTime cutoff);
}
