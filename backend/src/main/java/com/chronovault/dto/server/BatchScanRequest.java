package com.chronovault.dto.server;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 批量扫描服务器请求。
 */
public record BatchScanRequest(
    @NotEmpty(message = "服务器ID列表不能为空")
    List<Long> ids
) {}
