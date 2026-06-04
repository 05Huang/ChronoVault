package com.chronovault.repository;

import com.chronovault.entity.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findByStatusOrderByCreatedAtDesc(Alert.AlertStatus status);
    List<Alert> findBySeverityOrderByCreatedAtDesc(Alert.AlertSeverity severity);
    List<Alert> findAllByOrderByCreatedAtDesc();
    long countByStatus(Alert.AlertStatus status);
    long countBySeverity(Alert.AlertSeverity severity);

    @Query("SELECT COUNT(a) FROM Alert a WHERE a.createdAt >= CURRENT_DATE")
    long countToday();

    Page<Alert> findBySeverity(Alert.AlertSeverity severity, Pageable pageable);

    /** Paged query to prevent OOM on high-alert systems */
    Page<Alert> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
