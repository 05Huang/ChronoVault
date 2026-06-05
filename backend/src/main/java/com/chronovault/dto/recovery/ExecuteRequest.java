package com.chronovault.dto.recovery;

import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "执行恢复请求")
public record ExecuteRequest(
    @NotNull Long snapshotId,
    @Schema(description = "服务器 ID", example = "1")
    @NotNull Long serverId,
    String mode  // "full" or "partial", defaults to "full"
) {}
