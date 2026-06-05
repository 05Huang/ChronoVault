package com.chronovault.dto.settings;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "创建 API Key 响应")
public record CreateApiKeyResponse(
    ApiKeyDTO apiKey,
    String key
) {
    public static CreateApiKeyResponse of(ApiKeyDTO dto, String rawKey) {
        return new CreateApiKeyResponse(dto, rawKey);
    }
}
