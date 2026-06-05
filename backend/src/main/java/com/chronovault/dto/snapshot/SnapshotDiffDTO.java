package com.chronovault.dto.snapshot;

import com.chronovault.entity.SnapshotDiff;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "快照差异 DTO")
public record SnapshotDiffDTO(String path, String prev, String next, String changeType) {
    public static SnapshotDiffDTO from(SnapshotDiff d) {
        String changeType = d.getNextValue() != null && d.getPrevValue() == null ? "added"
                : d.getNextValue() == null ? "deleted" : "modified";
        return new SnapshotDiffDTO(d.getFilePath(), d.getPrevValue(), d.getNextValue(), changeType);
    }

    @Schema(description = "DiffSummary")
    public record DiffSummary(
            int addedCount,
            @Schema(description = "修改数量", example = "1")
            int modifiedCount,
            int deletedCount,
            List<SnapshotDiffDTO> diffs
    ) {}
}
