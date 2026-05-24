package com.chronovault.dto.server;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateContainerRequest(
        @NotBlank(message = "镜像名称不能为空")
        @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9_.\\-/:]{0,255}$", message = "无效的镜像名称")
        String image,

        @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9_.\\-]{0,127}$", message = "无效的容器名称")
        String name,

        // Format: "8080:80,443:443"
        String ports,

        // Format: "/host/path:/container/path,/host/path2:/container/path2"
        String volumes,

        // Format: "KEY1=val1,KEY2=val2"
        String env
) {}
