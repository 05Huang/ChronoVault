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

    /**
     * Find snapshots by server ID with database-level pagination.
     */
    Page<Snapshot> findByServerIdOrderByCreatedAtDesc(Long serverId, Pageable pageable);

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

    /**
     * Find the latest snapshot for each server — single query, no N+1.
     * Returns Snapshot entities ordered by server id.
     */
    @Query("SELECT s FROM Snapshot s WHERE s.id IN " +
           "(SELECT s2.id FROM Snapshot s2 WHERE s2.createdAt = " +
           "(SELECT MAX(s3.createdAt) FROM Snapshot s3 WHERE s3.server.id = s2.server.id))")
    List<Snapshot> findLatestPerServer();

    /**
     * Find recent snapshots with non-null change summary, limited — for dashboard overview.
     */
    @Query("SELECT s FROM Snapshot s WHERE s.changeSummaryJson IS NOT NULL AND s.changeSummaryJson <> '' ORDER BY s.createdAt DESC")
    List<Snapshot> findRecentWithChangeSummary(Pageable pageable);

    /**
     * Find unverified snapshots that are not stash type — for scheduled verification.
     * Avoids loading all snapshots into memory.
     */
    @Query("SELECT s FROM Snapshot s WHERE s.verified = false AND s.hash IS NOT NULL AND s.hash <> '' AND s.type <> 'STASH' ORDER BY s.createdAt ASC")
    List<Snapshot> findUnverifiedUnstashed(Pageable pageable);

    /**
     * Find expired stash snapshots older than the given threshold — for auto-expiry cleanup.
     */
    @Query("SELECT s FROM Snapshot s WHERE s.type = 'STASH' AND s.createdAt < :threshold")
    List<Snapshot> findExpiredStashes(@Param("threshold") java.time.LocalDateTime threshold);
}
