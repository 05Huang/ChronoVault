package com.chronovault.repository;

import com.chronovault.entity.SnapshotTag;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SnapshotTagRepository extends JpaRepository<SnapshotTag, Long> {
    List<SnapshotTag> findBySnapshotIdOrderByCreatedAtDesc(Long snapshotId);
    Optional<SnapshotTag> findBySnapshotIdAndName(Long snapshotId, String name);
    long countBySnapshotId(Long snapshotId);
    void deleteBySnapshotIdAndName(Long snapshotId, String name);
}
