package com.chronovault.dto.drift;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "文件状态漂移 DTO")
public record FileDrift(
    String filePath,
    @Schema(description = "漂移类型", example = "MODIFIED")
    String driftType,
    String currentHash,
    @Schema(description = "基线哈希值", example = "efgh5678")
    String baselineHash,
    String details
) {}