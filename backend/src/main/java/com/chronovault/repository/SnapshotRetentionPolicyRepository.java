package com.chronovault.repository;

import com.chronovault.entity.SnapshotRetentionPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SnapshotRetentionPolicyRepository extends JpaRepository<SnapshotRetentionPolicy, Long> {
    List<SnapshotRetentionPolicy> findByEnabledTrue();
    List<SnapshotRetentionPolicy> findByServerId(Long serverId);
}
