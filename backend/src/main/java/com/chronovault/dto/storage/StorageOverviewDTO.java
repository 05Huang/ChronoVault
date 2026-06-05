package com.chronovault.dto.storage;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "存储概览 DTO")
public record StorageOverviewDTO(
    Long id,
    @Schema(description = "类型", example = "FULL")
    String type,
    String name,
    @Schema(description = "已使用容量（字节）", example = "5368709120")
    Long usedBytes,
    Long totalBytes,
    @Schema(description = "使用率百分比", example = "50.0")
    Double usagePercent,
    String status
) {}
