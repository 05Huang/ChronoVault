package com.chronovault.dto.agent;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Agent 任务进度上报请求")
public record AgentTaskProgressRequest(
    @Min(0) @Max(100) int progress,
    String message
) {}
