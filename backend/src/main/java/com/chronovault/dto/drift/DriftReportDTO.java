package com.chronovault.dto.drift;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "状态漂移报告 DTO")
public record DriftReportDTO(
    Long serverId,
    @Schema(description = "服务器名称", example = "web-server-01")
    String serverName,
    int totalChanges,
    @Schema(description = "容器漂移列表")
    List<ContainerDrift> containerDrifts,
    List<FileDrift> fileDrifts,
    @Schema(description = "端口漂移列表")
    List<PortDrift> portDrifts,
    String status,
    String scannedAt
) {}