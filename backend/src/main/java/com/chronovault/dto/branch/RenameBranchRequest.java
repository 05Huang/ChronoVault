package com.chronovault.dto.branch;

import jakarta.validation.constraints.NotBlank;

public record RenameBranchRequest(
    @NotBlank(message = "分支名称不能为空") String name
) {}
