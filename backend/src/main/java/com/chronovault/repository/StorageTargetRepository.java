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
}
