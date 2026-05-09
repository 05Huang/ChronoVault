package com.chronovault.agent;

import com.chronovault.entity.*;
import com.chronovault.repository.*;
import com.chronovault.task.AsyncTaskManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
                                              String agentVersion, String capabilities) {
        Server server = serverRepository.findByAgentId(agentId);

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
            server.setIp(ip);
            server.setOs(os);
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
