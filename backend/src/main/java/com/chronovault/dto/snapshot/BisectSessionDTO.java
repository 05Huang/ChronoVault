package com.chronovault.dto.snapshot;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Bisect 会话状态 DTO")
public record BisectSessionDTO(
    String sessionId,
    @Schema(description = "服务器 ID", example = "1")
    Long serverId,
    Long goodSnapshotId,
    @Schema(description = "异常快照 ID", example = "10")
    Long badSnapshotId,
    Long currentSnapshotId,
    @Schema(description = "当前检测快照名称", example = "snap-005")
    String currentSnapshotName,
    int stepsRemaining,
    @Schema(description = "总步骤数", example = "5")
    int totalSteps,
    String status,
    @Schema(description = "问题快照名称")
    String culpritSnapshotName,
    List<SnapshotDTO> candidateSnapshots
) {}