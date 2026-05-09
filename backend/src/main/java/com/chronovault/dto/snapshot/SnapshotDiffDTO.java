package com.chronovault.dto.snapshot;

import com.chronovault.entity.SnapshotDiff;

public record SnapshotDiffDTO(String path, String prev, String next) {
    public static SnapshotDiffDTO from(SnapshotDiff d) {
        return new SnapshotDiffDTO(d.getFilePath(), d.getPrevValue(), d.getNextValue());
    }
}
