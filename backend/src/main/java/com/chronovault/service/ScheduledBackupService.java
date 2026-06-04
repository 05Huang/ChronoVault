package com.chronovault.service;

import com.chronovault.dto.scheduledbackup.CreateScheduledBackupRequest;
import com.chronovault.dto.scheduledbackup.ScheduledBackupDTO;
import com.chronovault.entity.ScheduledBackup;
import com.chronovault.entity.Server;
import com.chronovault.entity.Snapshot;
import com.chronovault.entity.StorageTarget;
import com.chronovault.entity.User;
import com.chronovault.exception.BadRequestException;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.ScheduledBackupRepository;
import com.chronovault.repository.ServerRepository;
import com.chronovault.repository.StorageTargetRepository;
import com.chronovault.repository.UserRepository;
import com.chronovault.snapshot.SnapshotEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledBackupService {

    private final ScheduledBackupRepository scheduledBackupRepository;
    private final ServerRepository serverRepository;
    private final StorageTargetRepository storageTargetRepository;
    private final UserRepository userRepository;
    private final SnapshotEngine snapshotEngine;

    @Transactional(readOnly = true)
    public List<ScheduledBackupDTO> getAll(Long userId) {
        return scheduledBackupRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(ScheduledBackupDTO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ScheduledBackupDTO getById(Long id) {
        ScheduledBackup sb = scheduledBackupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("定时备份不存在: " + id));
        return ScheduledBackupDTO.from(sb);
    }

    @Transactional
    public ScheduledBackupDTO create(CreateScheduledBackupRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在"));
        Server server = serverRepository.findById(request.serverId())
                .orElseThrow(() -> new ResourceNotFoundException("服务器不存在: " + request.serverId()));

        StorageTarget storageTarget = null;
        if (request.storageTargetId() != null) {
            storageTarget = storageTargetRepository.findById(request.storageTargetId())
                    .orElseThrow(() -> new ResourceNotFoundException("存储目标不存在: " + request.storageTargetId()));
        }

        // Validate cron expression
        try {
            CronExpression.parse(request.cronExpression());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("无效的 Cron 表达式: " + request.cronExpression());
        }

        ScheduledBackup sb = ScheduledBackup.builder()
                .user(user)
                .server(server)
                .storageTarget(storageTarget)
                .name(request.name())
                .cronExpression(request.cronExpression())
                .paths(request.paths())
                .excludes(request.excludes())
                .nextRunAt(computeNextRun(request.cronExpression()))
                .build();

        return ScheduledBackupDTO.from(scheduledBackupRepository.save(sb));
    }

    @Transactional
    public ScheduledBackupDTO toggleEnabled(Long id) {
        ScheduledBackup sb = scheduledBackupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("定时备份不存在: " + id));
        sb.setEnabled(!sb.getEnabled());
        if (sb.getEnabled()) {
            sb.setNextRunAt(computeNextRun(sb.getCronExpression()));
        } else {
            sb.setNextRunAt(null);
        }
        return ScheduledBackupDTO.from(sb);
    }

    @Transactional
    public void delete(Long id) {
        if (!scheduledBackupRepository.existsById(id)) {
            throw new ResourceNotFoundException("定时备份不存在: " + id);
        }
        scheduledBackupRepository.deleteById(id);
    }

    @Scheduled(fixedRate = 60000) // Check every minute
    @Transactional
    public void executeDue() {
        try {
            LocalDateTime now = LocalDateTime.now();
            List<ScheduledBackup> due = scheduledBackupRepository.findByEnabledTrueAndNextRunAtBefore(now);
            log.debug("[SCHEDULED_BACKUP] Found {} due backups", due.size());
            for (ScheduledBackup sb : due) {
                try {
                    executeSingle(sb);
                } catch (Exception e) {
                    log.error("[SCHEDULED_BACKUP] Failed to execute backup {}: {}", sb.getId(), e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("[SCHEDULED_BACKUP] Failed to query due backups: {}", e.getMessage(), e);
        }
    }

    private void executeSingle(ScheduledBackup sb) {
        log.info("Executing scheduled backup: id={}, name={}, server={}", sb.getId(), sb.getName(), sb.getServer().getName());
        sb.setLastRunAt(LocalDateTime.now());
        sb.setRunCount(sb.getRunCount() + 1);
        sb.setLastStatus(ScheduledBackup.RunStatus.RUNNING);
        try {
            String title = "定时备份: " + sb.getName();
            snapshotEngine.createSnapshot(sb.getServer(), sb.getStorageTarget(), title,
                    "由定时任务 #" + sb.getId() + " 自动创建", Snapshot.SnapshotType.FULL, sb.getUser().getId(),
                    null, null);
            sb.setLastStatus(ScheduledBackup.RunStatus.SUCCESS);
            sb.setLastError(null);
            log.info("Scheduled backup completed: id={}", sb.getId());
        } catch (Exception e) {
            sb.setLastStatus(ScheduledBackup.RunStatus.FAILED);
            sb.setLastError(e.getMessage());
            log.error("Scheduled backup failed: id={}, error={}", sb.getId(), e.getMessage());
        }
        sb.setNextRunAt(computeNextRun(sb.getCronExpression()));
    }

    private LocalDateTime computeNextRun(String cronExpr) {
        try {
            CronExpression cron = CronExpression.parse(cronExpr);
            return cron.next(LocalDateTime.now());
        } catch (Exception e) {
            log.warn("Invalid cron expression: {}", cronExpr);
            return null;
        }
    }
}
