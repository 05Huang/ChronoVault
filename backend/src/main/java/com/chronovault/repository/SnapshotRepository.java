package com.chronovault.repository;

import com.chronovault.entity.Snapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SnapshotRepository extends JpaRepository<Snapshot, Long> {
    List<Snapshot> findByServerIdOrderByCreatedAtDesc(Long serverId);
    long countByStatus(Snapshot.SnapshotStatus status);
}
