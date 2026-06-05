package com.chronovault.dto.storage;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "存储分布 DTO")
public record StorageDistributionDTO(
    String name,
    @Schema(description = "大小（字节）", example = "1048576")
    Long sizeBytes,
    Double percent,
    String type
) {}
