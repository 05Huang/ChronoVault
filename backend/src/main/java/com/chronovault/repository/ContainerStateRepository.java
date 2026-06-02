package com.chronovault.repository;

import com.chronovault.entity.ContainerState;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ContainerStateRepository extends JpaRepository<ContainerState, Long> {
    List<ContainerState> findBySnapshotIdOrderByContainerNameAsc(Long snapshotId);
}