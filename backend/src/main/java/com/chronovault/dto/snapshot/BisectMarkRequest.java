package com.chronovault.dto.snapshot;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record BisectMarkRequest(
    @NotNull(message = "快照ID不能为空")
    Long snapshotId,
    @NotBlank(message = "判定结果不能为空")
    @Pattern(regexp = "^(good|bad|skip)$", message = "判定结果必须为 good、bad 或 skip")
    String verdict
) {}