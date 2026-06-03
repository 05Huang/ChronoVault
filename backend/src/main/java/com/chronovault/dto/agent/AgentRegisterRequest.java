package com.chronovault.dto.agent;

import jakarta.validation.constraints.NotBlank;

public record AgentRegisterRequest(
    @NotBlank(message = "agentId 不能为空") String agentId,
    @NotBlank(message = "name 不能为空") String name,
    String ip, String os, String agentVersion, String capabilities,
    Long serverId
) {}
