package com.chronovault.controller;

import com.chronovault.dto.recovery.ExecuteRequest;
import com.chronovault.dto.recovery.JobStatusDTO;
import com.chronovault.dto.recovery.MigrateRequest;
import com.chronovault.dto.recovery.SimulateRequest;
import com.chronovault.service.RecoveryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecoveryControllerTest {

    @Mock private RecoveryService recoveryService;

    @InjectMocks
    private RecoveryController controller;

    @Test
    void simulate_returnsJobStatus() {
        JobStatusDTO status = new JobStatusDTO(1L, "RECOVERY", "RUNNING", 50, "2min", "server1", 1L);
        when(recoveryService.simulate(any(SimulateRequest.class))).thenReturn(status);
        SimulateRequest request = new SimulateRequest(1L, 1L);
        var response = controller.simulate(request);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
    }

    @Test
    void execute_returnsJobStatus() {
        JobStatusDTO status = new JobStatusDTO(1L, "RECOVERY", "COMPLETED", 100, "0s", "server1", 1L);
        when(recoveryService.execute(any(ExecuteRequest.class))).thenReturn(status);
        ExecuteRequest request = new ExecuteRequest(1L, 1L, "full");
        var response = controller.execute(request);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void migrate_returnsJobStatus() {
        JobStatusDTO status = new JobStatusDTO(1L, "MIGRATION", "RUNNING", 30, "5min", "server2", 1L);
        when(recoveryService.migrate(any(MigrateRequest.class))).thenReturn(status);
        MigrateRequest request = new MigrateRequest(1L, 2L, 1L);
        var response = controller.migrate(request);
        assertEquals(200, response.getStatusCode().value());
    }
}