package com.chronovault.dto.server;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CloneServerRequest(
    @NotNull Long sourceServerId,
    @NotBlank(message = "目标服务器IP不能为空")
    String targetServerIp,
    String targetName,
    Integer targetSshPort,
    String targetSshUsername
) {}