package com.chronovault.dto.scheduledbackup;

import com.chronovault.entity.ScheduledBackup;
import java.time.format.DateTimeFormatter;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "定时备份 DTO")
public record ScheduledBackupDTO(
    Long id, String name, Long serverId, String serverName,
    @Schema(description = "存储目标 ID")
    Long storageTargetId, String cronExpression, boolean enabled,
    String paths, String excludes,
    @Schema(description = "上次执行时间", example = "2026-06-06T02:00:00")
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
