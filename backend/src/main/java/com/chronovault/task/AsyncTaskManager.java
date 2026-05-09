package com.chronovault.task;

import com.chronovault.entity.AsyncTask;
import com.chronovault.entity.AsyncTask.TaskStatus;
import com.chronovault.entity.Event;
import com.chronovault.entity.Server;
import com.chronovault.entity.User;
import com.chronovault.repository.AsyncTaskRepository;
import com.chronovault.websocket.EventWebSocketHandler;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncTaskManager {

    private final AsyncTaskRepository taskRepository;
    private final EventWebSocketHandler wsHandler;

    @Autowired
    @Qualifier("taskExecutor")
    private ThreadPoolTaskExecutor taskExecutor;

    private final Map<Long, Boolean> cancellationFlags = new ConcurrentHashMap<>();

    public AsyncTask submit(TaskType type, Long serverId, Long userId, String message,
                            Consumer<AsyncTask> taskBody) {
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

        taskExecutor.submit(() -> executeTask(taskId, taskBody));
        return task;
    }

    private void executeTask(Long taskId, Consumer<AsyncTask> taskBody) {
        AsyncTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null) return;

        task.setStatus(TaskStatus.RUNNING);
        task.setStartedAt(LocalDateTime.now());
        taskRepository.save(task);

        broadcastTaskEvent(task, "任务开始: " + task.getMessage());

        try {
            taskBody.accept(task);

            if (!isCancelled(taskId)) {
                task.setStatus(TaskStatus.COMPLETED);
                task.setProgress(100);
                task.setCompletedAt(LocalDateTime.now());
                taskRepository.save(task);
                broadcastTaskEvent(task, "任务完成: " + task.getMessage());
            }
        } catch (Exception e) {
            log.error("Task {} failed: {}", taskId, e.getMessage(), e);
            task.setStatus(TaskStatus.FAILED);
            task.setError(e.getMessage());
            task.setCompletedAt(LocalDateTime.now());
            taskRepository.save(task);
            broadcastTaskEvent(task, "任务失败: " + e.getMessage());
        } finally {
            cancellationFlags.remove(taskId);
        }
    }

    public void updateProgress(Long taskId, int progress, String message) {
        AsyncTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null) return;

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

    public AsyncTask getStatus(Long taskId) {
        return taskRepository.findById(taskId).orElse(null);
    }

    private void broadcastTaskEvent(AsyncTask task, String message) {
        Event event = Event.builder()
                .level(task.getStatus() == TaskStatus.FAILED ? Event.EventLevel.ERR : Event.EventLevel.INFO)
                .message(message)
                .source("task:" + task.getType().name())
                .task(task)
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
