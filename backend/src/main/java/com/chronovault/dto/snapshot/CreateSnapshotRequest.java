package com.chronovault.dto.snapshot;

import jakarta.validation.constraints.NotNull;

public record CreateSnapshotRequest(
    @NotNull Long serverId,
    Long storageTargetId,
    String type,
    String note
) {}
