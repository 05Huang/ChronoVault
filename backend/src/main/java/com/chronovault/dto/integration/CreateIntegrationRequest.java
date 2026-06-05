package com.chronovault.dto.integration;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "创建集成请求")
public record CreateIntegrationRequest(
    @NotBlank String type,
    @Schema(description = "名称")
    @NotBlank String name,
    String url
) {}
