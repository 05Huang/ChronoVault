package com.chronovault.dto.branch;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "创建分支请求")
public record CreateBranchRequest(
    @NotBlank(message = "分支名称不能为空")
    @Schema(description = "名称")
    String name,
    String description,
    Long fromSnapshotId
) {}