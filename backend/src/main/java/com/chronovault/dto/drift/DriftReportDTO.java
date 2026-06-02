package com.chronovault.dto.drift;

import java.util.List;

public record DriftReportDTO(
    Long serverId,
    String serverName,
    int totalChanges,
    List<ContainerDrift> containerDrifts,
    List<FileDrift> fileDrifts,
    List<PortDrift> portDrifts,
    String status,
    String scannedAt
) {}