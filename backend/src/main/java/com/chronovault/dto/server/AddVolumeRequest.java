package com.chronovault.dto.server;

import jakarta.validation.constraints.NotBlank;

public record AddVolumeRequest(
        @NotBlank(message = "容器路径不能为空")
        String containerPath,

        @NotBlank(message = "主机路径不能为空")
        String hostPath
) {}
