package com.chronovault.repository;

import com.chronovault.entity.Volume;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VolumeRepository extends JpaRepository<Volume, Long> {
    List<Volume> findByServerId(Long serverId);
    void deleteByServerId(Long serverId);
}
