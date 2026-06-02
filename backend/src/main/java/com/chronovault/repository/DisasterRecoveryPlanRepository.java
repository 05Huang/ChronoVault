package com.chronovault.repository;

import com.chronovault.entity.DisasterRecoveryPlan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisasterRecoveryPlanRepository extends JpaRepository<DisasterRecoveryPlan, Long> {
}