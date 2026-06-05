package com.chronovault.dto.recovery;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "恢复任务状态 DTO")
public record JobStatusDTO(
    Long id,
    @Schema(description = "类型", example = "FULL")
    String type,
    String status,
    @Schema(description = "任务进度百分比（0-100）", example = "75")
    Integer progress,
    String estimatedTime,
    @Schema(description = "目标服务器", example = "web-server-02")
    String targetServer,
    Long recoveryPointId
) {}
