package com.chronovault.dto.scheduledbackup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateScheduledBackupRequest(
    @NotNull Long serverId,
    Long storageTargetId,
    @NotBlank String name,
    @NotBlank String cronExpression,
    String paths,
    String excludes
) {}
