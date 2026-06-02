package com.chronovault.dto.snapshot;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateSnapshotRequest(
    @NotNull Long serverId,
    Long storageTargetId,
    String type,
    String note,
    List<String> paths,
    List<String> excludes
) {}
