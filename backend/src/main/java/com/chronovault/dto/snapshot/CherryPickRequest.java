package com.chronovault.dto.snapshot;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CherryPickRequest(
    @NotEmpty(message = "至少需要选择一个文件")
    List<String> files,
    @NotNull(message = "目标服务器不能为空")
    Long targetServerId
) {}