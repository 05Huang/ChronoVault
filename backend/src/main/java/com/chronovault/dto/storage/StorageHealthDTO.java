package com.chronovault.dto.storage;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "存储健康状态 DTO")
public record StorageHealthDTO(
    String status,
    @Schema(description = "每秒 IO 操作数", example = "1200")
    String iops,
    String latency,
    @Schema(description = "吞吐量", example = "100MB/s")
    String throughput,
    Integer errorCount
) {}
