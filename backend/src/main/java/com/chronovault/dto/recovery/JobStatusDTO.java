package com.chronovault.dto.recovery;

public record JobStatusDTO(
    Long id,
    String type,
    String status,
    Integer progress,
    String estimatedTime,
    String targetServer,
    Long recoveryPointId
) {}
