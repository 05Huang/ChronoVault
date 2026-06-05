package com.chronovault.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "StorageSummaryDTO")
public record StorageSummaryDTO(
    String type,
    @Schema(description = "名称")
    String name,
    Long usedBytes,
    @Schema(description = "总容量（字节）", example = "10737418240")
    Long totalBytes,
    Double usagePercent
) {}
