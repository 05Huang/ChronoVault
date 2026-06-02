package com.chronovault.service;

import com.chronovault.entity.DisasterRecoveryPlan;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.DisasterRecoveryPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DisasterRecoveryPlanService {

    private final DisasterRecoveryPlanRepository planRepository;

    @Transactional(readOnly = true)
    public List<DisasterRecoveryPlan> getPlans() {
        return planRepository.findAll();
    }

    @Transactional(readOnly = true)
    public DisasterRecoveryPlan getPlan(Long id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("恢复计划不存在: " + id));
    }

    @Transactional
    public DisasterRecoveryPlan createPlan(DisasterRecoveryPlan plan) {
        return planRepository.save(plan);
    }

    @Transactional
    public DisasterRecoveryPlan updatePlan(Long id, DisasterRecoveryPlan updates) {
        DisasterRecoveryPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("恢复计划不存在: " + id));
        if (updates.getName() != null) plan.setName(updates.getName());
        if (updates.getDescription() != null) plan.setDescription(updates.getDescription());
        if (updates.getSteps() != null) plan.setSteps(updates.getSteps());
        if (updates.getEstimatedRto() != null) plan.setEstimatedRto(updates.getEstimatedRto());
        if (updates.getEstimatedRpo() != null) plan.setEstimatedRpo(updates.getEstimatedRpo());
        if (updates.getStatus() != null) plan.setStatus(updates.getStatus());
        return planRepository.save(plan);
    }

    @Transactional
    public void deletePlan(Long id) {
        planRepository.deleteById(id);
    }

    /**
     * Execute a recovery plan — mark as executed.
     * In a real implementation, this would trigger actual recovery steps.
     */
    @Transactional
    public DisasterRecoveryPlan executePlan(Long id) {
        DisasterRecoveryPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("恢复计划不存在: " + id));
        plan.setLastExecutedAt(LocalDateTime.now());
        plan.setStatus(DisasterRecoveryPlan.PlanStatus.ACTIVE);
        log.info("Disaster recovery plan {} executed: {}", id, plan.getName());
        return planRepository.save(plan);
    }
}