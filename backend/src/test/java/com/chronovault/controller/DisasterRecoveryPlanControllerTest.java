package com.chronovault.controller;

import com.chronovault.entity.DisasterRecoveryPlan;
import com.chronovault.service.DisasterRecoveryPlanService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DisasterRecoveryPlanControllerTest {

    @Mock private DisasterRecoveryPlanService planService;

    @InjectMocks
    private DisasterRecoveryPlanController controller;

    @Test
    void getPlans_returnsList() {
        when(planService.getPlans()).thenReturn(List.of());
        var response = controller.getPlans();
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void getPlan_returnsPlan() {
        DisasterRecoveryPlan plan = DisasterRecoveryPlan.builder().id(1L).name("DR Plan").build();
        when(planService.getPlan(1L)).thenReturn(plan);
        var response = controller.getPlan(1L);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void createPlan_succeeds() {
        DisasterRecoveryPlan plan = DisasterRecoveryPlan.builder().id(1L).name("New Plan").build();
        when(planService.createPlan(any(DisasterRecoveryPlan.class))).thenReturn(plan);
        var response = controller.createPlan(new DisasterRecoveryPlan());
        assertEquals(201, response.getStatusCode().value());
    }

    @Test
    void updatePlan_succeeds() {
        DisasterRecoveryPlan plan = DisasterRecoveryPlan.builder().id(1L).name("Updated Plan").build();
        when(planService.updatePlan(eq(1L), any(DisasterRecoveryPlan.class))).thenReturn(plan);
        var response = controller.updatePlan(1L, new DisasterRecoveryPlan());
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void deletePlan_succeeds() {
        doNothing().when(planService).deletePlan(1L);
        var response = controller.deletePlan(1L);
        assertEquals(200, response.getStatusCode().value());
        verify(planService).deletePlan(1L);
    }
}