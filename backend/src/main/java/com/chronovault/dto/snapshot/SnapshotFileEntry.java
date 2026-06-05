package com.chronovault.dto.snapshot;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "快照文件条目 DTO")
public record SnapshotFileEntry(
    String path,
    @Schema(description = "名称")
    String name,
    long size,
    @Schema(description = "类型", example = "FULL")
    String type,
    String modifiedAt
) {}