package com.chronovault.dto.snapshot;

import com.chronovault.entity.SnapshotDiff;

import java.util.List;

public record SnapshotDiffDTO(String path, String prev, String next, String changeType) {
    public static SnapshotDiffDTO from(SnapshotDiff d) {
        String changeType = d.getNextValue() != null && d.getPrevValue() == null ? "added"
                : d.getNextValue() == null ? "deleted" : "modified";
        return new SnapshotDiffDTO(d.getFilePath(), d.getPrevValue(), d.getNextValue(), changeType);
    }

    public record DiffSummary(
            int addedCount,
            int modifiedCount,
            int deletedCount,
            List<SnapshotDiffDTO> diffs
    ) {}
}
