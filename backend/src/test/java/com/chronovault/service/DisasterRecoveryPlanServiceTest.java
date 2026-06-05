package com.chronovault.service;

import com.chronovault.entity.DisasterRecoveryPlan;
import com.chronovault.entity.Server;
import com.chronovault.exception.BadRequestException;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.DisasterRecoveryPlanRepository;
import com.chronovault.repository.ServerRepository;
import com.chronovault.ssh.SshConnection;
import com.chronovault.ssh.SshConnectionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DisasterRecoveryPlanServiceTest {

    @Mock private DisasterRecoveryPlanRepository planRepository;
    @Mock private ServerRepository serverRepository;
    @Mock private SshConnectionManager sshManager;
    @Mock private SshConnection sshConnection;

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

    @Test
    void executePlan_existing_setsStatusAndTimestamp() {
        DisasterRecoveryPlan plan = DisasterRecoveryPlan.builder().id(1L).name("DR Plan")
                .status(DisasterRecoveryPlan.PlanStatus.DRAFT).build();
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.executePlan(1L);

        assertEquals(DisasterRecoveryPlan.PlanStatus.ACTIVE, result.getStatus());
        assertNotNull(result.getLastExecutedAt());
        verify(planRepository).save(plan);
    }

    @Test
    void executePlan_nonExisting_throwsException() {
        when(planRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.executePlan(999L));
    }

    @Test
    void updatePlan_partialUpdate_onlyUpdatesProvidedFields() {
        DisasterRecoveryPlan existing = DisasterRecoveryPlan.builder().id(1L)
                .name("Old").description("Old desc").estimatedRto(30).estimatedRpo(60).build();
        when(planRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DisasterRecoveryPlan updates = DisasterRecoveryPlan.builder().name("New").build();
        var result = service.updatePlan(1L, updates);

        assertEquals("New", result.getName());
        assertEquals("Old desc", result.getDescription());
        assertEquals(Integer.valueOf(30), result.getEstimatedRto());
    }

    @Test
    void updatePlan_allFields_updatesAll() {
        DisasterRecoveryPlan existing = DisasterRecoveryPlan.builder().id(1L).name("Old").build();
        when(planRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DisasterRecoveryPlan updates = DisasterRecoveryPlan.builder()
                .name("New").description("New desc").estimatedRto(15).estimatedRpo(30)
                .status(DisasterRecoveryPlan.PlanStatus.ACTIVE).build();

        var result = service.updatePlan(1L, updates);

        assertEquals("New", result.getName());
        assertEquals("New desc", result.getDescription());
        assertEquals(Integer.valueOf(15), result.getEstimatedRto());
        assertEquals(Integer.valueOf(30), result.getEstimatedRpo());
        assertEquals(DisasterRecoveryPlan.PlanStatus.ACTIVE, result.getStatus());
    }

    // =====================================================================
    // Simulation Tests
    // =====================================================================

    @Test
    @SuppressWarnings("unchecked")
    void simulatePlan_validPlan_returnsReport() {
        DisasterRecoveryPlan plan = DisasterRecoveryPlan.builder().id(1L).name("Web Server Recovery")
                .steps("1. Restore files from backup\n2. Restart nginx service\n3. Verify connectivity")
                .estimatedRto(15).estimatedRpo(5).build();
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));

        var result = service.simulatePlan(1L);

        assertNotNull(result);
        assertEquals(1L, result.get("planId"));
        assertEquals("Web Server Recovery", result.get("planName"));
        assertEquals(3, result.get("totalSteps"));
        assertEquals(15, result.get("estimatedRto"));
        assertEquals(5, result.get("estimatedRpo"));
        List<Map<String, Object>> steps = (List<Map<String, Object>>) result.get("steps");
        assertNotNull(steps);
        assertEquals(3, steps.size());
    }

    @Test
    void simulatePlan_nonExistingPlan_throwsException() {
        when(planRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.simulatePlan(999L));
    }

    @Test
    void simulatePlan_emptySteps_throwsBadRequest() {
        DisasterRecoveryPlan plan = DisasterRecoveryPlan.builder().id(1L).name("Empty Plan")
                .steps("").build();
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        assertThrows(BadRequestException.class, () -> service.simulatePlan(1L));
    }

    @Test
    void simulatePlan_nullSteps_throwsBadRequest() {
        DisasterRecoveryPlan plan = DisasterRecoveryPlan.builder().id(1L).name("Null Steps Plan")
                .steps(null).build();
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        assertThrows(BadRequestException.class, () -> service.simulatePlan(1L));
    }

    // =====================================================================
    // Execute Script Tests
    // =====================================================================

    @Test
    @SuppressWarnings("unchecked")
    void executeScript_validPlan_executesCommands() throws Exception {
        Server server = Server.builder().id(1L).name("Web Server").ip("10.0.0.1").build();
        DisasterRecoveryPlan plan = DisasterRecoveryPlan.builder().id(1L).name("Recovery Plan")
                .steps("systemctl restart nginx\necho 'recovery complete'").build();

        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(serverRepository.findById(1L)).thenReturn(Optional.of(server));
        when(sshManager.getConnection(server)).thenReturn(sshConnection);
        when(sshConnection.executeCommand(anyString(), any(java.time.Duration.class)))
                .thenReturn(new SshConnection.CommandResult(0, "ok", ""));

        var result = service.executeScript(1L, 1L);

        assertNotNull(result);
        assertEquals(1L, result.get("planId"));
        assertEquals(2, result.get("totalCommands"));
        assertEquals(2, result.get("successCount"));
        assertEquals(0, result.get("failCount"));
        List<Map<String, Object>> results = (List<Map<String, Object>>) result.get("results");
        assertEquals(2, results.size());
    }

    @Test
    void executeScript_planNotFound_throwsException() {
        when(planRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.executeScript(999L, 1L));
    }

    @Test
    void executeScript_serverNotFound_throwsException() {
        DisasterRecoveryPlan plan = DisasterRecoveryPlan.builder().id(1L).name("Plan").steps("echo test").build();
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(serverRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.executeScript(1L, 999L));
    }

    @Test
    void executeScript_emptySteps_throwsBadRequest() {
        DisasterRecoveryPlan plan = DisasterRecoveryPlan.builder().id(1L).name("Plan").steps("").build();
        Server server = Server.builder().id(1L).name("Server").build();
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(serverRepository.findById(1L)).thenReturn(Optional.of(server));
        assertThrows(BadRequestException.class, () -> service.executeScript(1L, 1L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void executeScript_commandFails_reportsFailure() throws Exception {
        Server server = Server.builder().id(1L).name("Server").build();
        DisasterRecoveryPlan plan = DisasterRecoveryPlan.builder().id(1L).name("Plan")
                .steps("failing-command\nsuccess-command").build();

        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(serverRepository.findById(1L)).thenReturn(Optional.of(server));
        when(sshManager.getConnection(server)).thenReturn(sshConnection);
        when(sshConnection.executeCommand(eq("failing-command"), any()))
                .thenReturn(new SshConnection.CommandResult(1, "", "command not found"));
        when(sshConnection.executeCommand(eq("success-command"), any()))
                .thenReturn(new SshConnection.CommandResult(0, "ok", ""));

        var result = service.executeScript(1L, 1L);

        assertEquals(2, result.get("totalCommands"));
        assertEquals(1, result.get("successCount"));
        assertEquals(1, result.get("failCount"));
    }

    @Test
    void executeScript_sshConnectionFails_throwsBadRequest() throws Exception {
        Server server = Server.builder().id(1L).name("Server").build();
        DisasterRecoveryPlan plan = DisasterRecoveryPlan.builder().id(1L).name("Plan")
                .steps("echo test").build();

        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(serverRepository.findById(1L)).thenReturn(Optional.of(server));
        when(sshManager.getConnection(server)).thenThrow(new java.io.IOException("Connection refused"));

        assertThrows(BadRequestException.class, () -> service.executeScript(1L, 1L));
    }

    // =====================================================================
    // Playbook Template Tests
    // =====================================================================

    @Test
    @SuppressWarnings("unchecked")
    void getPlaybookTemplates_returnsThreeTemplates() {
        var templates = service.getPlaybookTemplates();

        assertNotNull(templates);
        assertEquals(3, templates.size());

        // Verify web server template
        var webTemplate = (Map<String, Object>) templates.get(0);
        assertEquals("web-server", webTemplate.get("id"));
        assertEquals("Web 服务器恢复模板", webTemplate.get("name"));
        assertNotNull(webTemplate.get("steps"));
        assertEquals(15, webTemplate.get("estimatedRto"));

        // Verify database server template
        var dbTemplate = (Map<String, Object>) templates.get(1);
        assertEquals("database-server", dbTemplate.get("id"));
        assertEquals("数据库服务器恢复模板", dbTemplate.get("name"));
        assertEquals(30, dbTemplate.get("estimatedRto"));

        // Verify cache server template
        var cacheTemplate = (Map<String, Object>) templates.get(2);
        assertEquals("cache-server", cacheTemplate.get("id"));
        assertEquals("缓存服务器恢复模板", cacheTemplate.get("name"));
        assertEquals(5, cacheTemplate.get("estimatedRto"));
    }
}