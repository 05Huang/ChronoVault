package com.chronovault.repository;

import com.chronovault.entity.SnapshotTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface SnapshotTagRepository extends JpaRepository<SnapshotTag, Long> {
    List<SnapshotTag> findBySnapshotIdOrderByCreatedAtDesc(Long snapshotId);
    Optional<SnapshotTag> findBySnapshotIdAndName(Long snapshotId, String name);
    long countBySnapshotId(Long snapshotId);
    void deleteBySnapshotIdAndName(Long snapshotId, String name);

    /**
     * Batch load tags for multiple snapshots — avoids N+1 when loading snapshot lists.
     */
    @Query("SELECT t FROM SnapshotTag t WHERE t.snapshot.id IN :snapshotIds ORDER BY t.snapshot.id, t.createdAt DESC")
    List<SnapshotTag> findBySnapshotIdsIn(@Param("snapshotIds") List<Long> snapshotIds);
}
