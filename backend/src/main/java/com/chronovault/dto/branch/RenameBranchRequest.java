package com.chronovault.dto.branch;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "重命名分支请求")
public record RenameBranchRequest(
    @NotBlank(message = "分支名称不能为空") String name
) {}
