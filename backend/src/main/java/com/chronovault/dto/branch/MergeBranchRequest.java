package com.chronovault.dto.branch;

import jakarta.validation.constraints.NotNull;

public record MergeBranchRequest(
    @NotNull Long sourceBranchId,
    @NotNull Long targetBranchId
) {}