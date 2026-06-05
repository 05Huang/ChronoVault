package com.chronovault.dto.server;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "添加 Docker 卷请求")
public record AddVolumeRequest(
        @NotBlank(message = "容器路径不能为空")
        @Schema(description = "容器内路径", example = "/app/data")
        String containerPath,

        @NotBlank(message = "主机路径不能为空")
        String hostPath
) {}
