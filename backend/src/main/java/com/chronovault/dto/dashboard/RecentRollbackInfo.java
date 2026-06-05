package com.chronovault.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "最近回滚信息 DTO")
public record RecentRollbackInfo(
    String lastRollbackTime,
    @Schema(description = "最近回滚用户", example = "admin")
    String lastRollbackUser,
    String lastRollbackSnapshot
) {}