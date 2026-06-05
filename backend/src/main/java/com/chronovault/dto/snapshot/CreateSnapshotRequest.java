package com.chronovault.dto.snapshot;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "创建快照请求")
public record CreateSnapshotRequest(
    @NotNull(message = "服务器ID不能为空")
    @Schema(description = "服务器 ID", example = "1")
    Long serverId,
    Long storageTargetId,
    @Pattern(regexp = "^(FULL|INCREMENTAL|DIFF)?$", message = "快照类型必须为 FULL、INCREMENTAL 或 DIFF")
    @Schema(description = "类型", example = "FULL")
    String type,
    @Size(max = 200, message = "标题不能超过200个字符")
    @Schema(description = "快照标题", example = "生产环境每日快照")
    String title,
    @Size(max = 500, message = "备注不能超过500个字符")
    @Schema(description = "备注说明")
    String note,
    List<String> paths,
    List<String> excludes
) {}
