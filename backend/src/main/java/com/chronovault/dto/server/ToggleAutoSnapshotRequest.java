package com.chronovault.dto.server;

import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 切换自动快照的请求。
 */
@Schema(description = "切换自动快照请求")
public record ToggleAutoSnapshotRequest(
    @NotNull(message = "enabled 字段不能为空")
    Boolean enabled
) {}
