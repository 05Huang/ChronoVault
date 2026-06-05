package com.chronovault.dto.agent;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Agent 任务失败上报请求")
public record AgentTaskFailRequest(String error) {}
