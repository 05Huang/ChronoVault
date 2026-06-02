package com.chronovault.dto.snapshot;

import jakarta.validation.constraints.NotNull;

public record BisectMarkRequest(
    @NotNull Long snapshotId,
    @NotNull String verdict
) {}