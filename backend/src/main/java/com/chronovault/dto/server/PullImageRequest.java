package com.chronovault.dto.server;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 拉取 Docker 镜像请求。
 */
@Schema(description = "拉取 Docker 镜像请求")
public record PullImageRequest(
    @NotBlank(message = "镜像名称不能为空")
    String image
) {}
