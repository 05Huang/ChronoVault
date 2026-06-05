package com.chronovault.dto.stash;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "创建 Stash 请求")
public record CreateStashRequest(String note) {}
