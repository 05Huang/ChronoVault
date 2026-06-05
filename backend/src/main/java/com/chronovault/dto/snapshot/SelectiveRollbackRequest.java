package com.chronovault.dto.snapshot;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 选择性回滚请求 — 指定要回滚的配置文件或包版本。
 * 每个 item 应包含 "type"（config/package）及对应字段。
 */
@Schema(description = "选择性回滚请求")
public record SelectiveRollbackRequest(
    @NotEmpty(message = "至少需要选择一个回滚项目")
    List<Map<String, String>> items
) {}
