package com.chronovault.repository;

import com.chronovault.entity.SnapshotHook;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SnapshotHookRepository extends JpaRepository<SnapshotHook, Long> {
    List<SnapshotHook> findByServerIdAndHookTypeAndEnabledOrderByOrderIndexAsc(
            Long serverId, SnapshotHook.HookType hookType);
    List<SnapshotHook> findByServerIdOrderByOrderIndexAsc(Long serverId);
}