package com.chronovault.repository;

import com.chronovault.entity.StorageTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface StorageTargetRepository extends JpaRepository<StorageTarget, Long> {
    List<StorageTarget> findByUserId(Long userId);
    List<StorageTarget> findByType(StorageTarget.StorageType type);

    @Query("SELECT COALESCE(SUM(s.usedBytes), 0) FROM StorageTarget s")
    Long sumUsedBytes();

    @Query("SELECT COALESCE(SUM(s.totalBytes), 0) FROM StorageTarget s")
    Long sumTotalBytes();

    /**
     * Find the preferred storage target: first non-LOCAL target, or any target if none exist.
     * Avoids loading all targets just to pick one.
     */
    @Query("SELECT s FROM StorageTarget s WHERE s.type <> com.chronovault.entity.StorageTarget$StorageType.LOCAL ORDER BY s.id ASC LIMIT 1")
    StorageTarget findFirstNonLocal();

    /**
     * Find any storage target (first by ID).
     */
    @Query("SELECT s FROM StorageTarget s ORDER BY s.id ASC LIMIT 1")
    StorageTarget findFirst();
}
