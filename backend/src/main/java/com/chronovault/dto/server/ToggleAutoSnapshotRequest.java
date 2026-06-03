package com.chronovault.dto.server;

import jakarta.validation.constraints.NotNull;

/**
 * 切换自动快照的请求。
 */
public record ToggleAutoSnapshotRequest(
    @NotNull(message = "enabled 字段不能为空")
    Boolean enabled
) {}
