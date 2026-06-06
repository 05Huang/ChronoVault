package com.chronovault.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Anomaly detection result: compares current server state against historical baseline
 * to automatically flag unexpected changes.
 */
@Schema(description = "AI 异常检测结果")
public record AnomalyDetectionDTO(
    @Schema(description = "服务器 ID") Long serverId,
    @Schema(description = "服务器名称") String serverName,
    @Schema(description = "检测到的异常列表") List<Anomaly> anomalies,
    @Schema(description = "检测摘要") String summary,
    @Schema(description = "检测时间") LocalDateTime detectedAt
) {
    /**
     * A single detected anomaly.
     */
    @Schema(description = "单个异常项")
    public record Anomaly(
        @Schema(description = "异常类型: PORT/SERVICE/PACKAGE/CONFIG/DOCKER/CRONTAB/OS") String type,
        @Schema(description = "严重级别: CRITICAL/WARNING/INFO") String severity,
        @Schema(description = "异常标题") String title,
        @Schema(description = "异常详情") String detail,
        @Schema(description = "关联服务器 ID") Long serverId,
        @Schema(description = "附加元数据") Map<String, Object> metadata
    ) {}
}
