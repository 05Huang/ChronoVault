package com.chronovault.repository;

import com.chronovault.entity.SnapshotDiff;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SnapshotDiffRepository extends JpaRepository<SnapshotDiff, Long> {
    List<SnapshotDiff> findBySnapshotId(Long snapshotId);
}
