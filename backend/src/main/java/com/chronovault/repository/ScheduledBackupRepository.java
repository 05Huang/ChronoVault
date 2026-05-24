package com.chronovault.repository;

import com.chronovault.entity.ScheduledBackup;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface ScheduledBackupRepository extends JpaRepository<ScheduledBackup, Long> {
    List<ScheduledBackup> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<ScheduledBackup> findByEnabledTrueAndNextRunAtBefore(LocalDateTime now);
}
