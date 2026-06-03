package com.chronovault.dto.snapshot;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 批量打标签请求。
 */
public record BatchTagRequest(
    @NotEmpty(message = "快照ID列表不能为空")
    List<Long> snapshotIds,
    @NotBlank(message = "标签名称不能为空")
    String tagName,
    String color
) {}
