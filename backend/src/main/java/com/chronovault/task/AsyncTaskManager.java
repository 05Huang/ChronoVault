package com.chronovault.task;

import com.chronovault.entity.AsyncTask;
import com.chronovault.entity.AsyncTask.TaskStatus;
import com.chronovault.entity.Event;
import com.chronovault.entity.Server;
import com.chronovault.entity.User;
import com.chronovault.repository.AsyncTaskRepository;
import com.chronovault.websocket.EventWebSocketHandler;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncTaskManager {

    private final AsyncTaskRepository taskRepository;
    private final EventWebSocketHandler wsHandler;
    private final MeterRegistry meterRegistry;

    @Autowired
    @Qualifier("taskExecutor")
    private ThreadPoolTaskExecutor taskExecutor;

    private final Map<Long, Boolean> cancellationFlags = new ConcurrentHashMap<>();
    private final Map<Long, Long> creationTimestamps = new ConcurrentHashMap<>();
    private volatile boolean shuttingDown = false;

    public AsyncTask submit(TaskType type, Long serverId, Long userId, String message,
                            Consumer<AsyncTask> taskBody) {
        if (shuttingDown) {
            throw new IllegalStateException("Server is shutting down, no new tasks accepted");
        }
        AsyncTask task = AsyncTask.builder()
                .type(type)
                .status(TaskStatus.PENDING)
                .message(message)
                .build();
        if (serverId != null) {
            Server server = new Server();
            server.setId(serverId);
            task.setServer(server);
        }
        if (userId != null) {
            User user = new User();
            user.setId(userId);
            task.setUser(user);
        }
        task = taskRepository.save(task);
        final Long taskId = task.getId();
        creationTimestamps.put(taskId, System.currentTimeMillis());
        log.info("[TASK_CREATE] [task={}] [type={}] [server={}] {}",
                taskId, type, serverId, message);

        taskExecutor.submit(() -> executeTask(taskId, taskBody));
        return task;
    }

    private void executeTask(Long taskId, Consumer<AsyncTask> taskBody) {
        Long createdMs = creationTimestamps.get(taskId);
        long queueDelayMs = createdMs != null ? System.currentTimeMillis() - createdMs : 0;

        log.info("[TASK_START] [task={}] Queue wait: {}ms, executing...", taskId, queueDelayMs);
        AsyncTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            log.warn("[TASK_START] [task={}] Not found in database, skipping", taskId);
            return;
        }

        long executionStartMs = System.currentTimeMillis();
        try {
            task.setStatus(TaskStatus.RUNNING);
            task.setStartedAt(LocalDateTime.now());
            taskRepository.save(task);

            broadcastTaskEvent(task, "任务开始: " + task.getMessage());
            log.info("[TASK_RUNNING] [task={}] [type={}] [server={}] Execution started (queue delay={}ms)",
                    taskId, task.getType(), task.getServer() != null ? task.getServer().getId() : "-", queueDelayMs);

            taskBody.accept(task);

            if (!isCancelled(taskId)) {
                long durationMs = System.currentTimeMillis() - executionStartMs;
                task.setStatus(TaskStatus.COMPLETED);
                task.setProgress(100);
                task.setCompletedAt(LocalDateTime.now());
                taskRepository.save(task);
                broadcastTaskEvent(task, "任务完成: " + task.getMessage());
                log.info("[TASK_COMPLETE] [task={}] [type={}] Completed in {}ms", taskId, task.getType(), durationMs);
                meterRegistry.timer("async.task.duration", "type", task.getType().name(), "result", "success")
                        .record(durationMs, TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - executionStartMs;
            log.error("[TASK_FAILED] [task={}] [type={}] Failed after {}ms: {}", taskId,
                    task.getType(), durationMs, e.getMessage(), e);
            try {
                task.setStatus(TaskStatus.FAILED);
                task.setError(e.getMessage());
                task.setCompletedAt(LocalDateTime.now());
                taskRepository.save(task);
                broadcastTaskEvent(task, "任务失败: " + e.getMessage());
                meterRegistry.timer("async.task.duration", "type", task.getType().name(), "result", "failed")
                        .record(durationMs, TimeUnit.MILLISECONDS);
            } catch (Exception saveErr) {
                log.error("[TASK_FAILED] [task={}] Failed to save error status: {}", taskId, saveErr.getMessage());
            }
        } finally {
            cancellationFlags.remove(taskId);
        }
    }

    public void updateProgress(Long taskId, int progress, String message) {
        AsyncTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null) return;

        Long createdMs = creationTimestamps.get(taskId);
        long elapsedMs = createdMs != null ? System.currentTimeMillis() - createdMs : 0;

        log.info("[TASK_PROGRESS] [task={}] [type={}] {}% {} (elapsed={}ms)",
                taskId, task.getType(), progress, message, elapsedMs);

        task.setProgress(progress);
        task.setMessage(message);
        taskRepository.save(task);

        Map<String, Object> payload = Map.of(
                "type", "TASK_PROGRESS",
                "id", taskId,
                "taskType", task.getType().name(),
                "status", task.getStatus().name(),
                "progress", progress,
                "message", message != null ? message : ""
        );
        wsHandler.sendToTopic("/topic/tasks/" + taskId, payload);
        wsHandler.sendToTopic("/topic/tasks", payload);
    }

    public void cancel(Long taskId) {
        cancellationFlags.put(taskId, true);
        AsyncTask task = taskRepository.findById(taskId).orElse(null);
        if (task != null && task.getStatus() == TaskStatus.RUNNING) {
            task.setStatus(TaskStatus.CANCELLED);
            task.setCompletedAt(LocalDateTime.now());
            taskRepository.save(task);
            broadcastTaskEvent(task, "任务已取消");
        }
    }

    public boolean isCancelled(Long taskId) {
        return cancellationFlags.getOrDefault(taskId, false);
    }

    @PreDestroy
    public void shutdown() {
        log.info("AsyncTaskManager shutting down — waiting for running tasks to complete...");
        shuttingDown = true;

        taskExecutor.setWaitForTasksToCompleteOnShutdown(true);
        taskExecutor.setAwaitTerminationSeconds(60);
        taskExecutor.shutdown();

        try {
            if (taskExecutor.getThreadPoolExecutor().awaitTermination(60, TimeUnit.SECONDS)) {
                log.info("All async tasks completed gracefully");
            } else {
                log.warn("Some async tasks did not complete within 60s timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Shutdown interrupted", e);
        }
    }

    public AsyncTask getStatus(Long taskId) {
        return taskRepository.findById(taskId).orElse(null);
    }

    /**
     * Clean up old completed/failed/cancelled tasks older than the specified days.
     * @param daysOld Number of days to keep records for
     * @return Number of records deleted
     */
    @org.springframework.transaction.annotation.Transactional
    public int cleanupOldTasks(int daysOld) {
        java.time.LocalDateTime cutoff = java.time.LocalDateTime.now().minusDays(daysOld);
        java.util.List<AsyncTask> oldTasks = taskRepository.findAll().stream()
                .filter(t -> t.getCreatedAt() != null && t.getCreatedAt().isBefore(cutoff))
                .filter(t -> t.getStatus() == TaskStatus.COMPLETED
                        || t.getStatus() == TaskStatus.FAILED
                        || t.getStatus() == TaskStatus.CANCELLED)
                .toList();

        if (!oldTasks.isEmpty()) {
            taskRepository.deleteAll(oldTasks);
            log.info("Cleaned up {} old async tasks (older than {} days)", oldTasks.size(), daysOld);
        }
        return oldTasks.size();
    }

    private void broadcastTaskEvent(AsyncTask task, String message) {
        Event event = Event.builder()
                .level(task.getStatus() == TaskStatus.FAILED ? Event.EventLevel.ERR : Event.EventLevel.INFO)
                .message(message)
                .source("task:" + task.getType().name())
                .task(task)
                .createdAt(LocalDateTime.now())
                .build();
        wsHandler.broadcastEvent(event);

        Map<String, Object> taskPayload = Map.of(
                "type", "TASK_STATUS",
                "id", task.getId(),
                "taskType", task.getType().name(),
                "status", task.getStatus().name(),
                "progress", task.getProgress() != null ? task.getProgress() : 0,
                "message", message != null ? message : ""
        );
        wsHandler.sendToTopic("/topic/tasks/" + task.getId(), taskPayload);
        wsHandler.sendToTopic("/topic/tasks", taskPayload);
    }
}
