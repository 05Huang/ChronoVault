package com.chronovault.agent;

import com.chronovault.entity.*;
import com.chronovault.repository.*;
import com.chronovault.task.AsyncTaskManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentCommunicationService {

    private final ServerRepository serverRepository;
    private final AgentInfoRepository agentInfoRepository;
    private final AsyncTaskRepository taskRepository;
    private final ContainerRepository containerRepository;
    private final VolumeRepository volumeRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public Map<String, Object> registerAgent(String agentId, String name, String ip, String os,
                                              String agentVersion, String capabilities, Long serverId) {
        Server server = null;

        // If serverId is provided, try to link to that existing server
        if (serverId != null) {
            server = serverRepository.findById(serverId).orElse(null);
            if (server != null) {
                server.setAgentId(agentId);
                if (ip != null) server.setIp(ip);
                if (os != null) server.setOs(os);
                server.setStatus(Server.ServerStatus.RUNNING);
                server = serverRepository.save(server);
            }
        }

        // Fallback: find by agentId
        if (server == null) {
            server = serverRepository.findByAgentId(agentId);
        }

        if (server == null) {
            // Create new server
            User systemUser = new User();
            systemUser.setId(1L); // Default to first user

            server = Server.builder()
                    .user(systemUser)
                    .name(name != null ? name : "Agent-" + agentId.substring(0, 8))
                    .ip(ip)
                    .os(os)
                    .agentId(agentId)
                    .status(Server.ServerStatus.RUNNING)
                    .build();
            server = serverRepository.save(server);
        } else {
            if (ip != null) server.setIp(ip);
            if (os != null) server.setOs(os);
            server.setStatus(Server.ServerStatus.RUNNING);
            server = serverRepository.save(server);
        }

        // Create or update agent info
        AgentInfo agentInfo = agentInfoRepository.findByServerId(server.getId()).orElse(null);
        if (agentInfo == null) {
            agentInfo = AgentInfo.builder()
                    .server(server)
                    .agentVersion(agentVersion)
                    .capabilities(capabilities)
                    .build();
        } else {
            agentInfo.setAgentVersion(agentVersion);
            agentInfo.setCapabilities(capabilities);
        }
        agentInfo.setLastHeartbeatAt(LocalDateTime.now());
        agentInfo.setStatus("ONLINE");
        agentInfoRepository.save(agentInfo);

        return Map.of(
                "serverId", server.getId(),
                "agentId", agentId,
                "status", "REGISTERED"
        );
    }

    @Transactional
    public void heartbeat(String agentId, Map<String, Object> metrics) {
        AgentInfo agentInfo = agentInfoRepository.findByServerAgentId(agentId).orElse(null);
        if (agentInfo == null) {
            log.warn("Heartbeat from unknown agent: {}", agentId);
            return;
        }

        agentInfo.setLastHeartbeatAt(LocalDateTime.now());
        agentInfo.setStatus("ONLINE");
        agentInfoRepository.save(agentInfo);

        // Update server status
        Server server = agentInfo.getServer();
        server.setStatus(Server.ServerStatus.RUNNING);
        if (metrics.containsKey("uptime")) {
            server.setUptimeSeconds(((Number) metrics.get("uptime")).longValue());
        }
        serverRepository.save(server);
    }

    public List<AsyncTask> getPendingTasks(String agentId) {
        AgentInfo agentInfo = agentInfoRepository.findByServerAgentId(agentId).orElse(null);
        if (agentInfo == null) return List.of();

        return taskRepository.findByServerIdAndStatusIn(
                agentInfo.getServer().getId(),
                List.of(AsyncTask.TaskStatus.PENDING)
        );
    }

    @Transactional
    public void updateTaskProgress(Long taskId, int progress, String message) {
        AsyncTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null) return;

        task.setProgress(progress);
        task.setMessage(message);
        if (progress >= 100) {
            task.setStatus(AsyncTask.TaskStatus.COMPLETED);
            task.setCompletedAt(LocalDateTime.now());
        }
        taskRepository.save(task);
    }

    @Transactional
    public void completeTask(Long taskId, String result) {
        AsyncTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null) return;

        task.setStatus(AsyncTask.TaskStatus.COMPLETED);
        task.setProgress(100);
        task.setResult(result);
        task.setCompletedAt(LocalDateTime.now());
        taskRepository.save(task);
    }

    @Transactional
    public void failTask(Long taskId, String error) {
        AsyncTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null) return;

        task.setStatus(AsyncTask.TaskStatus.FAILED);
        task.setError(error);
        task.setCompletedAt(LocalDateTime.now());
        taskRepository.save(task);
    }

    /**
     * Check for agents that haven't sent a heartbeat in 120 seconds.
     * Marks them as OFFLINE and updates server status accordingly.
     * Runs every 60 seconds.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void checkStaleHeartbeats() {
        try {
            LocalDateTime staleThreshold = LocalDateTime.now().minusSeconds(120);
            List<AgentInfo> staleAgents = agentInfoRepository.findByStatusAndLastHeartbeatAtBefore(
                    "ONLINE", staleThreshold);

            if (staleAgents.isEmpty()) return;

            log.info("[AGENT_HEARTBEAT] Marking {} agents as OFFLINE (no heartbeat since {})",
                    staleAgents.size(), staleThreshold);

            for (AgentInfo agent : staleAgents) {
                agent.setStatus("OFFLINE");
                agentInfoRepository.save(agent);

                Server server = agent.getServer();
                if (server != null && server.getStatus() == Server.ServerStatus.RUNNING) {
                    server.setStatus(Server.ServerStatus.STOPPED);
                    serverRepository.save(server);
                    log.warn("[AGENT_HEARTBEAT] Server {} ({}) marked STOPPED (agent offline)", server.getName(), server.getIp());
                }
            }
        } catch (Exception e) {
            log.error("[AGENT_HEARTBEAT] Failed to check stale heartbeats: {}", e.getMessage(), e);
        }
    }

    @Transactional
    public void updateContainers(Long serverId, List<Map<String, Object>> containerData) {
        Server server = serverRepository.findById(serverId).orElse(null);
        if (server == null) return;

        containerRepository.deleteByServerId(serverId);

        for (Map<String, Object> data : containerData) {
            Container container = Container.builder()
                    .server(server)
                    .name((String) data.getOrDefault("name", "unknown"))
                    .type(Container.ContainerType.API)
                    .status(Container.ContainerStatus.RUNNING)
                    .build();

            String image = (String) data.getOrDefault("image", "");
            if (image.contains("nginx") || image.contains("httpd")) container.setType(Container.ContainerType.NGINX);
            else if (image.contains("mysql") || image.contains("postgres")) container.setType(Container.ContainerType.MYSQL);
            else if (image.contains("redis")) container.setType(Container.ContainerType.REDIS);

            String state = (String) data.getOrDefault("state", "");
            if ("running".equalsIgnoreCase(state)) container.setStatus(Container.ContainerStatus.RUNNING);
            else if ("paused".equalsIgnoreCase(state)) container.setStatus(Container.ContainerStatus.PAUSED);
            else container.setStatus(Container.ContainerStatus.STOPPED);

            containerRepository.save(container);
        }
    }
}
