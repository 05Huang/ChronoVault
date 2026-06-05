package com.chronovault.dto.settings;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "生成 API Key 请求")
public record GenerateKeyRequest(
    @NotBlank String name,
    String scope
) {}
