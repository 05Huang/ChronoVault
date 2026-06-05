package com.chronovault.dto.snapshot;

import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "启动 Bisect 会话请求")
public record BisectStartRequest(
    @NotNull Long serverId,
    @Schema(description = "正常快照 ID", example = "1")
    @NotNull Long goodSnapshotId,
    @NotNull Long badSnapshotId
) {}