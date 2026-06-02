package com.chronovault.repository;

import com.chronovault.entity.Snapshot;
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
}
