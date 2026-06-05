package com.chronovault.dto.snapshot;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "标记 Bisect 判定结果请求")
public record BisectMarkRequest(
    @NotNull(message = "快照ID不能为空")
    @Schema(description = "快照 ID", example = "1")
    Long snapshotId,
    @NotBlank(message = "判定结果不能为空")
    @Pattern(regexp = "^(good|bad|skip)$", message = "判定结果必须为 good、bad 或 skip")
    String verdict
) {}