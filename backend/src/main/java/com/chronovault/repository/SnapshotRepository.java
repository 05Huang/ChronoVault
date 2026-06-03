package com.chronovault.repository;

import com.chronovault.entity.Snapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface SnapshotRepository extends JpaRepository<Snapshot, Long> {
    List<Snapshot> findByServerIdOrderByCreatedAtDesc(Long serverId);
    List<Snapshot> findAllByOrderByCreatedAtDesc();
    long countByStatus(Snapshot.SnapshotStatus status);

    @Query("SELECT COUNT(s) FROM Snapshot s WHERE s.createdAt >= CURRENT_DATE")
    long countToday();

    List<Snapshot> findByServerIdAndCreatedAtBeforeOrderByCreatedAtAsc(Long serverId, LocalDateTime before);

    @Query("SELECT s FROM Snapshot s WHERE s.server.id = :serverId ORDER BY s.createdAt DESC")
    List<Snapshot> findLatestByServer(@Param("serverId") Long serverId);

    /**
     * Find snapshots by tag name using JOIN — avoids loading all snapshots and filtering in memory.
     * The relationship goes from SnapshotTag → Snapshot (Many-to-One).
     */
    @Query("SELECT DISTINCT t.snapshot FROM SnapshotTag t WHERE t.name = :tagName ORDER BY t.snapshot.createdAt DESC")
    List<Snapshot> findByTagName(@Param("tagName") String tagName);

    /**
     * Find snapshots by tag name with pagination.
     */
    @Query("SELECT DISTINCT t.snapshot FROM SnapshotTag t WHERE t.name = :tagName ORDER BY t.snapshot.createdAt DESC")
    Page<Snapshot> findByTagNamePaged(@Param("tagName") String tagName, Pageable pageable);

    /**
     * Find the most recent snapshot before a given snapshot for the same server — for diff comparison.
     */
    @Query("SELECT s FROM Snapshot s WHERE s.server.id = :serverId AND s.id <> :snapshotId ORDER BY s.createdAt DESC")
    List<Snapshot> findPreviousSnapshots(@Param("serverId") Long serverId, @Param("snapshotId") Long snapshotId, Pageable pageable);
}
