package com.chronovault.dto.agent;

import jakarta.validation.constraints.NotBlank;

public record AgentPendingTasksRequest(
    @NotBlank(message = "agentId 不能为空") String agentId
) {}
