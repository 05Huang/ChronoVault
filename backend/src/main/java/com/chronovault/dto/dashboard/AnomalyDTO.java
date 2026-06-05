package com.chronovault.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "异常事件 DTO")
public record AnomalyDTO(
    Long id,
    @Schema(description = "严重级别（CRITICAL/WARNING/INFO）", example = "WARNING")
    String severity,
    String title,
    @Schema(description = "边起始节点 ID", example = "node-1")
    String source,
    String time
) {}
