package com.chronovault.dto.agent;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Agent 任务结果上报请求")
public record AgentTaskResultRequest(String result) {}
