package com.chronovault.dto.agent;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Agent 注册请求")
public record AgentRegisterRequest(
    @NotBlank(message = "agentId 不能为空") String agentId,
    @Schema(description = "名称")
    @NotBlank(message = "name 不能为空") String name,
    String ip, String os, String agentVersion, String capabilities,
    Long serverId
) {}
