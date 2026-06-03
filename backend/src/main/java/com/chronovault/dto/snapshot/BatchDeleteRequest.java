package com.chronovault.dto.snapshot;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 批量删除快照请求。
 */
public record BatchDeleteRequest(
    @NotEmpty(message = "快照ID列表不能为空")
    List<Long> ids
) {}
