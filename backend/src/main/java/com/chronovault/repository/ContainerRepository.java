package com.chronovault.repository;

import com.chronovault.entity.Container;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ContainerRepository extends JpaRepository<Container, Long> {
    List<Container> findByServerId(Long serverId);
    void deleteByServerId(Long serverId);
}
