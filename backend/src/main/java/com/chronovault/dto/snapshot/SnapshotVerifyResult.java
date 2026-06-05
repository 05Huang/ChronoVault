package com.chronovault.dto.snapshot;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "快照验证结果 DTO")
public record SnapshotVerifyResult(
    Long snapshotId,
    @Schema(description = "是否验证通过", example = "true")
    boolean verified,
    String errors,
    long durationMs
) {}