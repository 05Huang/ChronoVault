package com.chronovault.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Backup strategy recommendation based on historical snapshot analysis.
 */
@Schema(description = "AI 备份策略推荐")
public record BackupRecommendationDTO(
    @Schema(description = "频率推荐") FrequencyRecommendation frequency,
    @Schema(description = "保留策略推荐") RetentionRecommendation retention,
    @Schema(description = "路径推荐") PathRecommendation paths,
    @Schema(description = "各服务器快照状况") List<ServerBackupSummary> servers,
    @Schema(description = "AI 生成的综合总结") String aiSummary
) {
    @Schema(description = "快照频率推荐")
    public record FrequencyRecommendation(
        @Schema(description = "推荐频率，如'每日'、'每12小时'") String suggestedFrequency,
        @Schema(description = "推荐理由") String reason,
        @Schema(description = "推荐的 cron 表达式") String cronExpression,
        @Schema(description = "优先级: HIGH/MEDIUM/INFO") String priority
    ) {}

    @Schema(description = "快照保留策略推荐")
    public record RetentionRecommendation(
        @Schema(description = "推荐保留天数") int suggestedRetainDays,
        @Schema(description = "推荐理由") String reason,
        @Schema(description = "推荐的目标剩余空间比例") String freeSpaceTarget,
        @Schema(description = "优先级: HIGH/MEDIUM/INFO") String priority
    ) {}

    @Schema(description = "备份路径推荐")
    public record PathRecommendation(
        @Schema(description = "建议优先备份的路径") List<String> priorityPaths,
        @Schema(description = "建议排除的路径") List<String> excludePaths,
        @Schema(description = "各服务器的个性化路径建议") List<ServerPathSuggestion> perServerSuggestions
    ) {}

    @Schema(description = "服务器级别的路径建议")
    public record ServerPathSuggestion(
        @Schema(description = "服务器 ID") Long serverId,
        @Schema(description = "检测到的应备份路径") List<String> suggestedPaths,
        @Schema(description = "建议依据") String reason
    ) {}

    @Schema(description = "单台服务器的备份状况摘要")
    public record ServerBackupSummary(
        @Schema(description = "服务器 ID") Long serverId,
        @Schema(description = "服务器名称") String serverName,
        @Schema(description = "快照总数") long snapshotCount,
        @Schema(description = "含 state.json 的快照数") long withStateJson,
        @Schema(description = "状态: NO_BACKUP/INSUFFICIENT/NO_STATE/ADEQUATE") String status,
        @Schema(description = "建议操作") String suggestion
    ) {}
}
