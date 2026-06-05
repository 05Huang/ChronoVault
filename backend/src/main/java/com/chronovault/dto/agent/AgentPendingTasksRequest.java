package com.chronovault.dto.agent;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Agent 获取待处理任务请求")
public record AgentPendingTasksRequest(
    @NotBlank(message = "agentId 不能为空") String agentId
) {}
