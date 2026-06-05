package com.chronovault.dto.settings;

import java.util.Map;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "更新 AI 配置请求")
public record UpdateAiConfigRequest(
    Map<String, Object> config
) {}
