package com.chronovault.dto.snapshot;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "选择性恢复请求")
public record SelectiveRestoreRequest(
    @NotEmpty(message = "至少需要选择一个恢复路径")
    @Schema(description = "备份路径列表", example = "[\"/etc\", \"/opt\"]")
    List<String> paths,
    String targetPath,
    boolean overwrite
) {}