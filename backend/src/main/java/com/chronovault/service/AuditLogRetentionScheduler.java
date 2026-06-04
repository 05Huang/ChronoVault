package com.chronovault.service;

import com.chronovault.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Periodically cleans up audit logs older than 90 days.
 * Runs daily at 2:30 AM to avoid overlap with other retention tasks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogRetentionScheduler {

    private static final int RETENTION_DAYS = 90;

    private final AuditLogRepository auditLogRepository;

    @Scheduled(cron = "0 30 2 * * *") // Every day at 2:30 AM
    @Transactional
    public void cleanupOldAuditLogs() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        log.info("[AUDIT_RETENTION] Cleaning up audit logs older than {} days (before {})", RETENTION_DAYS, cutoff);

        try {
            auditLogRepository.deleteByCreatedAtBefore(cutoff);
            log.info("[AUDIT_RETENTION] Audit log retention cleanup completed successfully");
        } catch (Exception e) {
            log.error("[AUDIT_RETENTION] Failed to clean up old audit logs: {}", e.getMessage(), e);
        }
    }
}
