package com.chronovault.dto.settings;

import com.chronovault.entity.ApiKey;
import java.time.format.DateTimeFormatter;

public record ApiKeyDTO(
    Long id, String name, String prefix, String scope,
    String lastUsedAt, String createdAt
) {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MMM d, HH:mm");

    public static ApiKeyDTO from(ApiKey k) {
        String lastUsed = k.getLastUsedAt() != null ? k.getLastUsedAt().format(FMT) : "";
        String created = k.getCreatedAt() != null ? k.getCreatedAt().format(FMT) : "";
        return new ApiKeyDTO(k.getId(), k.getName(), k.getPrefix(), k.getScope().name(), lastUsed, created);
    }
}
