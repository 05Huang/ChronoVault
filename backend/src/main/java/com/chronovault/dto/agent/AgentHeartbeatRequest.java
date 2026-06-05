package com.chronovault.dto.agent;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Agent 心跳请求")
public record AgentHeartbeatRequest(
    @NotBlank(message = "agentId 不能为空") String agentId,
    Map<String, Object> metrics
) {}
