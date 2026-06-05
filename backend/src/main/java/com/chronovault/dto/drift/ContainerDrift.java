package com.chronovault.dto.drift;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "容器状态漂移 DTO")
public record ContainerDrift(
    String containerName,
    @Schema(description = "当前状态", example = "ONLINE")
    String status,
    String driftType,
    String details
) {}