package com.chronovault.dto.recovery;

import jakarta.validation.constraints.NotNull;

public record ExecuteRequest(
    @NotNull Long snapshotId,
    @NotNull Long serverId,
    String mode  // "full" or "partial", defaults to "full"
) {}
