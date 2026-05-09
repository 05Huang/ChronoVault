package com.chronovault.repository;

import com.chronovault.entity.SnapshotManifest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SnapshotManifestRepository extends JpaRepository<SnapshotManifest, Long> {

    List<SnapshotManifest> findBySnapshotId(Long snapshotId);

    @Query("SELECT m FROM SnapshotManifest m WHERE m.snapshot.id = :snapshotId AND m.filePath LIKE CONCAT(:prefix, '%')")
    List<SnapshotManifest> findBySnapshotIdAndPathPrefix(@Param("snapshotId") Long snapshotId, @Param("prefix") String prefix);

    @Query("SELECT COUNT(m) FROM SnapshotManifest m WHERE m.snapshot.id = :snapshotId")
    long countBySnapshotId(@Param("snapshotId") Long snapshotId);
}
