package com.chronovault.dto.dashboard;

public record RecentRollbackInfo(
    String lastRollbackTime,
    String lastRollbackUser,
    String lastRollbackSnapshot
) {}