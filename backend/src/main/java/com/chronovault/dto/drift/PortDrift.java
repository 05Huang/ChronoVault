package com.chronovault.dto.drift;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "端口状态漂移 DTO")
public record PortDrift(
    int port,
    @Schema(description = "协议", example = "tcp")
    String protocol,
    String driftType,
    String details
) {}