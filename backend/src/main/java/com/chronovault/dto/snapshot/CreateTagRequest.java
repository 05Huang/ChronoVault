package com.chronovault.dto.snapshot;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTagRequest(
    @NotBlank(message = "标签名称不能为空")
    @Size(min = 1, max = 50, message = "标签名称长度必须在1-50个字符之间")
    String name,

    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "颜色格式必须为#RRGGBB")
    String color
) {
    public CreateTagRequest {
        if (color == null || color.isBlank()) {
            color = "#0058be";
        }
    }
}
