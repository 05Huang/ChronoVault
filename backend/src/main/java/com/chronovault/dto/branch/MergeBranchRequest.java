package com.chronovault.dto.branch;

import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "合并分支请求")
public record MergeBranchRequest(
    @NotNull Long sourceBranchId,
    @NotNull Long targetBranchId
) {}