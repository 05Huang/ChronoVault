package com.chronovault.dto.snapshot;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 批量打标签请求。
 */
public record BatchTagRequest(
    @NotEmpty(message = "快照ID列表不能为空")
    @Size(max = 100, message = "一次最多为100个快照打标签")
    List<Long> snapshotIds,
    @NotBlank(message = "标签名称不能为空")
    @Size(min = 1, max = 50, message = "标签名称长度不能超过50个字符")
    String tagName,
    @Pattern(regexp = "^(#[0-9a-fA-F]{6}|[a-z]+)?$", message = "颜色格式无效")
    String color
) {}
