package com.chronovault.dto.recovery;

import jakarta.validation.constraints.NotNull;

public record MigrateRequest(
    @NotNull Long sourceServerId,
    @NotNull Long targetServerId,
    Long snapshotId
) {}
