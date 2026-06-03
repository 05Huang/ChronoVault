package com.chronovault.controller;

import com.chronovault.agent.AgentCommunicationService;
import com.chronovault.dto.agent.*;
import com.chronovault.entity.AsyncTask;
import com.chronovault.task.TaskType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentControllerTest {

    @Mock
    private AgentCommunicationService agentService;

    @InjectMocks
    private AgentController controller;

    @Test
    void register_validRequest_returnsResult() {
        AgentRegisterRequest request = new AgentRegisterRequest(
                "agent-001", "test-server", "10.0.0.1", "Ubuntu 22.04", "0.1.0", "{}", null);
        Map<String, Object> result = Map.of("serverId", 1L, "agentId", "agent-001", "status", "registered");
        when(agentService.registerAgent("agent-001", "test-server", "10.0.0.1", "Ubuntu 22.04", "0.1.0", "{}", null))
                .thenReturn(result);

        var response = controller.register(request);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("agent-001", response.getBody().data().get("agentId"));
    }

    @Test
    void heartbeat_validRequest_succeeds() {
        AgentHeartbeatRequest request = new AgentHeartbeatRequest("agent-001", Map.of("timestamp", 12345L));
        doNothing().when(agentService).heartbeat("agent-001", Map.of("timestamp", 12345L));

        var response = controller.heartbeat(request);
        assertEquals(200, response.getStatusCode().value());
        verify(agentService).heartbeat("agent-001", Map.of("timestamp", 12345L));
    }

    @Test
    void getPendingTasks_validAgent_returnsTasks() {
        AgentPendingTasksRequest request = new AgentPendingTasksRequest("agent-001");
        AsyncTask task = new AsyncTask();
        task.setId(1L);
        task.setType(TaskType.SNAPSHOT);
        when(agentService.getPendingTasks("agent-001")).thenReturn(List.of(task));

        var response = controller.getPendingTasks(request);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().data().size());
    }

    @Test
    void updateProgress_validTask_succeeds() {
        AgentTaskProgressRequest request = new AgentTaskProgressRequest(50, "halfway");
        doNothing().when(agentService).updateTaskProgress(1L, 50, "halfway");

        var response = controller.updateProgress(1L, request);
        assertEquals(200, response.getStatusCode().value());
        verify(agentService).updateTaskProgress(1L, 50, "halfway");
    }

    @Test
    void completeTask_validTask_succeeds() {
        AgentTaskResultRequest request = new AgentTaskResultRequest("{\"status\":\"ok\"}");
        doNothing().when(agentService).completeTask(1L, "{\"status\":\"ok\"}");

        var response = controller.completeTask(1L, request);
        assertEquals(200, response.getStatusCode().value());
        verify(agentService).completeTask(1L, "{\"status\":\"ok\"}");
    }

    @Test
    void failTask_validTask_succeeds() {
        AgentTaskFailRequest request = new AgentTaskFailRequest("connection timeout");
        doNothing().when(agentService).failTask(1L, "connection timeout");

        var response = controller.failTask(1L, request);
        assertEquals(200, response.getStatusCode().value());
        verify(agentService).failTask(1L, "connection timeout");
    }
}