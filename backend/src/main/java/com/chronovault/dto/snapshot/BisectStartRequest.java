package com.chronovault.dto.snapshot;

import jakarta.validation.constraints.NotNull;

public record BisectStartRequest(
    @NotNull Long serverId,
    @NotNull Long goodSnapshotId,
    @NotNull Long badSnapshotId
) {}