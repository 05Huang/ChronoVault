package com.chronovault.dto.recovery;

import jakarta.validation.constraints.NotNull;

public record SimulateRequest(
    @NotNull Long snapshotId,
    @NotNull Long serverId
) {}
