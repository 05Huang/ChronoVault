package com.chronovault.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "snapshot_diffs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SnapshotDiff {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private Snapshot snapshot;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "prev_value", length = 500)
    private String prevValue;

    @Column(name = "next_value", length = 500)
    private String nextValue;
}
