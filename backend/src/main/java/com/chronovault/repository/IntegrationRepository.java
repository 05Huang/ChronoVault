package com.chronovault.repository;

import com.chronovault.entity.Integration;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface IntegrationRepository extends JpaRepository<Integration, Long> {
    List<Integration> findByUserId(Long userId);
}
