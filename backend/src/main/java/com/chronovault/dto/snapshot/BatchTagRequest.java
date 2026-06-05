package com.chronovault.dto.snapshot;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 批量打标签请求。
 */
@Schema(description = "批量打标签请求")
public record BatchTagRequest(
    @NotEmpty(message = "快照ID列表不能为空")
    @Size(max = 100, message = "一次最多为100个快照打标签")
    @Schema(description = "快照 ID 列表", example = "[1, 2, 3]")
    List<Long> snapshotIds,
    @NotBlank(message = "标签名称不能为空")
    @Size(min = 1, max = 50, message = "标签名称长度不能超过50个字符")
    @Schema(description = "标签名称", example = "production")
    String tagName,
    @Pattern(regexp = "^(#[0-9a-fA-F]{6}|[a-z]+)?$", message = "颜色格式无效")
    String color
) {}
