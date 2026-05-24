package com.chronovault.dto.scheduledbackup;

import com.chronovault.entity.ScheduledBackup;
import java.time.format.DateTimeFormatter;

public record ScheduledBackupDTO(
    Long id, String name, Long serverId, String serverName,
    Long storageTargetId, String cronExpression, boolean enabled,
    String paths, String excludes,
    String lastRunAt, String nextRunAt, String lastStatus, String lastError,
    int runCount, String createdAt
) {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public static ScheduledBackupDTO from(ScheduledBackup sb) {
        return new ScheduledBackupDTO(
            sb.getId(),
            sb.getName(),
            sb.getServer().getId(),
            sb.getServer().getName(),
            sb.getStorageTarget() != null ? sb.getStorageTarget().getId() : null,
            sb.getCronExpression(),
            Boolean.TRUE.equals(sb.getEnabled()),
            sb.getPaths(),
            sb.getExcludes(),
            sb.getLastRunAt() != null ? sb.getLastRunAt().format(FMT) : null,
            sb.getNextRunAt() != null ? sb.getNextRunAt().format(FMT) : null,
            sb.getLastStatus() != null ? sb.getLastStatus().name() : null,
            sb.getLastError(),
            sb.getRunCount(),
            sb.getCreatedAt() != null ? sb.getCreatedAt().format(FMT) : ""
        );
    }
}
