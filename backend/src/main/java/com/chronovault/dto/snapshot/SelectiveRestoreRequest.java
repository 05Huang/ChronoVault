package com.chronovault.dto.snapshot;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record SelectiveRestoreRequest(
    @NotEmpty(message = "至少需要选择一个恢复路径")
    List<String> paths,
    String targetPath,
    boolean overwrite
) {}