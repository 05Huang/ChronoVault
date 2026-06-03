package com.chronovault.dto.agent;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AgentTaskProgressRequest(
    @Min(0) @Max(100) int progress,
    String message
) {}
