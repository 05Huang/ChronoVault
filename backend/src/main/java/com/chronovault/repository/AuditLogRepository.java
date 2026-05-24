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
}
