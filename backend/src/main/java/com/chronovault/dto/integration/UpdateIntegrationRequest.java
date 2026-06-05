package com.chronovault.dto.integration;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "更新集成请求")
public record UpdateIntegrationRequest(Boolean active) {}
