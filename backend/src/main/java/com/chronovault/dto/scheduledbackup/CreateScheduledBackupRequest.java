package com.chronovault.dto.scheduledbackup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "创建定时备份请求")
public record CreateScheduledBackupRequest(
    @NotNull Long serverId,
    @Schema(description = "存储目标 ID")
    Long storageTargetId,
    @NotBlank String name,
    @Schema(description = "Cron 表达式", example = "0 2 * * *")
    @NotBlank String cronExpression,
    String paths,
    String excludes
) {}
