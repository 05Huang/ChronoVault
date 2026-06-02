package com.chronovault.dto.snapshot;

import java.util.List;

public record BisectSessionDTO(
    String sessionId,
    Long serverId,
    Long goodSnapshotId,
    Long badSnapshotId,
    Long currentSnapshotId,
    String currentSnapshotName,
    int stepsRemaining,
    int totalSteps,
    String status,
    String culpritSnapshotName,
    List<SnapshotDTO> candidateSnapshots
) {}