package com.chronovault.dto.agent;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record AgentHeartbeatRequest(
    @NotBlank(message = "agentId 不能为空") String agentId,
    Map<String, Object> metrics
) {}
