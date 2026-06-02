package com.chronovault.repository;

import com.chronovault.entity.ServerGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ServerGroupRepository extends JpaRepository<ServerGroup, Long> {
    List<ServerGroup> findByUserIdOrderByCreatedAtAsc(Long userId);
}