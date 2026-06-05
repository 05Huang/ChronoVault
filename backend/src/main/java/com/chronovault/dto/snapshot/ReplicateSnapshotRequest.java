package com.chronovault.dto.snapshot;

import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 快照复制到其他存储目标的请求。
 */
@Schema(description = "快照复制请求")
public record ReplicateSnapshotRequest(
    @NotNull(message = "目标存储ID不能为空")
    Long targetStorageId
) {}
