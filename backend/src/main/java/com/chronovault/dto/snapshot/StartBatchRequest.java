package com.chronovault.dto.snapshot;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 批量创建快照请求。
 */
@Schema(description = "批量创建快照请求")
public record StartBatchRequest(
    @NotEmpty(message = "服务器ID列表不能为空")
    @Schema(description = "服务器 ID 列表", example = "[1, 2, 3]")
    List<Long> serverIds,
    Long storageTargetId,
    String name
) {}
