package com.chronovault.dto.recovery;

import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "模拟恢复请求")
public record SimulateRequest(
    @NotNull Long snapshotId,
    @NotNull Long serverId
) {}
