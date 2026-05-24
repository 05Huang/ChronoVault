package com.chronovault.task;

import com.chronovault.entity.AsyncTask;
import com.chronovault.entity.AsyncTask.TaskStatus;
import com.chronovault.repository.AsyncTaskRepository;
import com.chronovault.websocket.EventWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AsyncTaskManagerTest {

    @Mock
    private AsyncTaskRepository taskRepository;

    @Mock
    private EventWebSocketHandler wsHandler;

    @Mock
    private ThreadPoolTaskExecutor taskExecutor;

    @InjectMocks
    private AsyncTaskManager taskManager;

    private AsyncTask testTask;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(taskManager, "taskExecutor", taskExecutor);
        testTask = AsyncTask.builder()
                .id(1L)
                .type(TaskType.SNAPSHOT)
                .status(TaskStatus.PENDING)
                .progress(0)
                .message("测试任务")
                .build();

        // Capture the Runnable submitted to taskExecutor and run it synchronously
        lenient().doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(taskExecutor).submit(any(Runnable.class));
    }

    @Test
    void submit_createsTaskAndExecutes() {
        when(taskRepository.save(any(AsyncTask.class))).thenReturn(testTask);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask));

        AsyncTask result = taskManager.submit(
                TaskType.SNAPSHOT, 1L, 1L, "创建快照", task -> {});

        assertNotNull(result);
        assertEquals(TaskType.SNAPSHOT, result.getType());
        verify(taskRepository, atLeast(1)).save(any(AsyncTask.class));
    }

    @Test
    void submit_successfulTask_setsCompletedStatus() {
        when(taskRepository.save(any(AsyncTask.class))).thenReturn(testTask);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask));

        taskManager.submit(TaskType.SNAPSHOT, 1L, 1L, "快照", task -> {
            // Task body succeeds
        });

        // Verify task was set to COMPLETED
        ArgumentCaptor<AsyncTask> captor = ArgumentCaptor.forClass(AsyncTask.class);
        verify(taskRepository, atLeast(2)).save(captor.capture());

        AsyncTask completedTask = captor.getAllValues().stream()
                .filter(t -> t.getStatus() == TaskStatus.COMPLETED)
                .findFirst()
                .orElse(null);
        assertNotNull(completedTask);
        assertEquals(100, completedTask.getProgress());
    }

    @Test
    void submit_failedTask_setsFailedStatus() {
        when(taskRepository.save(any(AsyncTask.class))).thenReturn(testTask);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask));

        taskManager.submit(TaskType.RECOVER, 1L, 1L, "恢复", task -> {
            throw new RuntimeException("恢复失败");
        });

        ArgumentCaptor<AsyncTask> captor = ArgumentCaptor.forClass(AsyncTask.class);
        verify(taskRepository, atLeast(2)).save(captor.capture());

        AsyncTask failedTask = captor.getAllValues().stream()
                .filter(t -> t.getStatus() == TaskStatus.FAILED)
                .findFirst()
                .orElse(null);
        assertNotNull(failedTask);
        assertEquals("恢复失败", failedTask.getError());
    }

    @Test
    void updateProgress_updatesAndBroadcasts() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask));
        when(taskRepository.save(any(AsyncTask.class))).thenReturn(testTask);

        taskManager.updateProgress(1L, 50, "进度 50%");

        assertEquals(50, testTask.getProgress());
        assertEquals("进度 50%", testTask.getMessage());
        verify(wsHandler).sendToTopic(eq("/topic/tasks/1"), any());
        verify(wsHandler).sendToTopic(eq("/topic/tasks"), any());
    }

    @Test
    void updateProgress_nonExistentTask_doesNothing() {
        when(taskRepository.findById(999L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> taskManager.updateProgress(999L, 50, "msg"));
        verify(taskRepository, never()).save(any());
    }

    @Test
    void cancel_setsCancelledStatus() {
        testTask.setStatus(TaskStatus.RUNNING);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask));
        when(taskRepository.save(any(AsyncTask.class))).thenReturn(testTask);

        taskManager.cancel(1L);

        assertEquals(TaskStatus.CANCELLED, testTask.getStatus());
        assertNotNull(testTask.getCompletedAt());
        assertTrue(taskManager.isCancelled(1L));
    }

    @Test
    void cancel_nonRunningTask_stillSetsFlag() {
        testTask.setStatus(TaskStatus.PENDING);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask));

        taskManager.cancel(1L);

        assertTrue(taskManager.isCancelled(1L));
    }

    @Test
    void getStatus_existingTask_returnsTask() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask));

        AsyncTask result = taskManager.getStatus(1L);
        assertNotNull(result);
        assertEquals(TaskType.SNAPSHOT, result.getType());
    }

    @Test
    void getStatus_nonExistent_returnsNull() {
        when(taskRepository.findById(999L)).thenReturn(Optional.empty());

        AsyncTask result = taskManager.getStatus(999L);
        assertNull(result);
    }

    @Test
    void submit_withNullServerAndUser_stillWorks() {
        AsyncTask taskNoRefs = AsyncTask.builder()
                .id(2L)
                .type(TaskType.HEALTH_CHECK)
                .status(TaskStatus.PENDING)
                .build();
        when(taskRepository.save(any(AsyncTask.class))).thenReturn(taskNoRefs);
        when(taskRepository.findById(2L)).thenReturn(Optional.of(taskNoRefs));

        AsyncTask result = taskManager.submit(
                TaskType.HEALTH_CHECK, null, null, "健康检查", t -> {});

        assertNotNull(result);
    }
}
