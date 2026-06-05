package com.chronovault.dto.server;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "创建 Docker 容器请求")
public record CreateContainerRequest(
        @NotBlank(message = "镜像名称不能为空")
        @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9_.\\-/:]{0,255}$", message = "无效的镜像名称")
        @Schema(description = "Docker 镜像名称", example = "nginx:1.24")
        String image,

        @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9_.\\-]{0,127}$", message = "无效的容器名称")
        @Schema(description = "名称")
        String name,

        // Format: "8080:80,443:443"
        @Schema(description = "端口映射", example = "80:80,443:443")
        String ports,

        // Format: "/host/path:/container/path,/host/path2:/container/path2"
        @Schema(description = "卷挂载", example = "/host:/container")
        String volumes,

        // Format: "KEY1=val1,KEY2=val2"
        String env
) {}
