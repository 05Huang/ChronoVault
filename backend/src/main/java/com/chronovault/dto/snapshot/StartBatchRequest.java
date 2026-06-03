package com.chronovault.dto.snapshot;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 批量创建快照请求。
 */
public record StartBatchRequest(
    @NotEmpty(message = "服务器ID列表不能为空")
    List<Long> serverIds,
    Long storageTargetId,
    String name
) {}
