package com.chronovault.dto.settings;

public record CreateApiKeyResponse(
    ApiKeyDTO apiKey,
    String key
) {
    public static CreateApiKeyResponse of(ApiKeyDTO dto, String rawKey) {
        return new CreateApiKeyResponse(dto, rawKey);
    }
}
