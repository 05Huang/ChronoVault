package com.chronovault.dto.recovery;

import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "服务器迁移请求")
public record MigrateRequest(
    @NotNull Long sourceServerId,
    @Schema(description = "目标服务器 ID", example = "2")
    @NotNull Long targetServerId,
    Long snapshotId
) {}
