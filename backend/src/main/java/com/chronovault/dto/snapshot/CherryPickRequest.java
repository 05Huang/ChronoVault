package com.chronovault.dto.snapshot;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Cherry-pick 变更请求")
public record CherryPickRequest(
    @NotEmpty(message = "至少需要选择一个文件")
    @Schema(description = "文件路径列表", example = "[\"/etc/nginx/nginx.conf\"]")
    List<String> files,
    @NotNull(message = "目标服务器不能为空")
    Long targetServerId
) {}