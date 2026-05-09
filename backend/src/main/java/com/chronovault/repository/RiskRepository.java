package com.chronovault.repository;

import com.chronovault.entity.Risk;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RiskRepository extends JpaRepository<Risk, Long> {
    List<Risk> findByStatusOrderByDiscoveredAtDesc(Risk.RiskStatus status);
    List<Risk> findAllByOrderByDiscoveredAtDesc();
    long countByLevel(Risk.RiskLevel level);
    long countByStatus(Risk.RiskStatus status);
}
