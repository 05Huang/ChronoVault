package com.chronovault.dto.server;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

public record UpdateSshConfigRequest(
        @Min(value = 1, message = "端口不能小于1")
        @Max(value = 65535, message = "端口不能大于65535")
        Integer port,

        String username,

        @Pattern(regexp = "^(KEY|PASSWORD)$", message = "认证方式必须为 KEY 或 PASSWORD")
        String authMethod,

        String credential
) {}
