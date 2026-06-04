package com.chronovault.service;

import com.chronovault.entity.DisasterRecoveryPlan;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.DisasterRecoveryPlanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DisasterRecoveryPlanServiceTest {

    @Mock private DisasterRecoveryPlanRepository planRepository;

    @InjectMocks
    private DisasterRecoveryPlanService service;

    @Test
    void getPlans_returnsAll() {
        DisasterRecoveryPlan plan = DisasterRecoveryPlan.builder().id(1L).name("DR Plan 1").build();
        when(planRepository.findAll()).thenReturn(List.of(plan));
        var result = service.getPlans();
        assertEquals(1, result.size());
    }

    @Test
    void getPlan_existing_returnsPlan() {
        DisasterRecoveryPlan plan = DisasterRecoveryPlan.builder().id(1L).name("DR Plan").build();
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        var result = service.getPlan(1L);
        assertEquals("DR Plan", result.getName());
    }

    @Test
    void getPlan_nonExisting_throwsException() {
        when(planRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.getPlan(999L));
    }

    @Test
    void createPlan_savesAndReturns() {
        DisasterRecoveryPlan plan = DisasterRecoveryPlan.builder().name("New Plan").build();
        when(planRepository.save(any(DisasterRecoveryPlan.class))).thenAnswer(inv -> {
            DisasterRecoveryPlan p = inv.getArgument(0);
            var field = DisasterRecoveryPlan.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(p, 1L);
            return p;
        });
        var result = service.createPlan(plan);
        assertNotNull(result);
        verify(planRepository).save(any(DisasterRecoveryPlan.class));
    }

    @Test
    void updatePlan_nonExisting_throwsException() {
        when(planRepository.findById(999L)).thenReturn(Optional.empty());
        DisasterRecoveryPlan updates = DisasterRecoveryPlan.builder().name("Updated").build();
        assertThrows(ResourceNotFoundException.class, () -> service.updatePlan(999L, updates));
    }

    @Test
    void updatePlan_existing_updatesFields() {
        DisasterRecoveryPlan existing = DisasterRecoveryPlan.builder().id(1L).name("Old Name").build();
        when(planRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        DisasterRecoveryPlan updates = DisasterRecoveryPlan.builder().name("New Name").build();

        var result = service.updatePlan(1L, updates);
        assertEquals("New Name", result.getName());
    }

    @Test
    void deletePlan_existing_deletes() {
        service.deletePlan(1L);
        verify(planRepository).deleteById(1L);
    }
}