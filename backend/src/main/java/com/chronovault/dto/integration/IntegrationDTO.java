package com.chronovault.dto.integration;

import com.chronovault.entity.Integration;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "集成配置 DTO")
public record IntegrationDTO(
    Long id, String type, String name, String url, Boolean active
) {
    public static IntegrationDTO from(Integration i) {
        return new IntegrationDTO(i.getId(), i.getType().name(), i.getName(), i.getUrl(), i.getActive());
    }
}
