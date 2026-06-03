package com.chronovault.dto.dashboard;

public record PendingAlertsInfo(
    Integer totalPending,
    Integer highRisk,
    Integer warnings
) {}