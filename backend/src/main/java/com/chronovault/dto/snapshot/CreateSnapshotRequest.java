package com.chronovault.dto.snapshot;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateSnapshotRequest(
    @NotNull(message = "服务器ID不能为空")
    Long serverId,
    Long storageTargetId,
    @Pattern(regexp = "^(FULL|INCREMENTAL|DIFF)?$", message = "快照类型必须为 FULL、INCREMENTAL 或 DIFF")
    String type,
    @Size(max = 500, message = "备注不能超过500个字符")
    String note,
    List<String> paths,
    List<String> excludes
) {}
