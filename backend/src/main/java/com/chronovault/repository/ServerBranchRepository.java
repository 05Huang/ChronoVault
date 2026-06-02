package com.chronovault.repository;

import com.chronovault.entity.ServerBranch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ServerBranchRepository extends JpaRepository<ServerBranch, Long> {
    List<ServerBranch> findByServerIdOrderByCreatedAtAsc(Long serverId);
    Optional<ServerBranch> findByServerIdAndIsDefaultTrue(Long serverId);
    Optional<ServerBranch> findByServerIdAndName(Long serverId, String name);
    boolean existsByServerIdAndName(Long serverId, String name);
}