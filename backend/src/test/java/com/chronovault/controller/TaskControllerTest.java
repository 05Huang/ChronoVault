package com.chronovault.controller;

import com.chronovault.entity.AsyncTask;
import com.chronovault.repository.AsyncTaskRepository;
import com.chronovault.task.AsyncTaskManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskControllerTest {

    @Mock private AsyncTaskRepository taskRepository;
    @Mock private AsyncTaskManager taskManager;

    @InjectMocks
    private TaskController controller;

    @Test
    void getTasks_returnsList() {
        when(taskRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
        var response = controller.getTasks();
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void getTask_existing_returnsTask() {
        AsyncTask task = AsyncTask.builder().id(1L).status(AsyncTask.TaskStatus.RUNNING).build();
        when(taskManager.getStatus(1L)).thenReturn(task);
        var response = controller.getTask(1L);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void getTask_nonExisting_returns404() {
        when(taskManager.getStatus(999L)).thenReturn(null);
        var response = controller.getTask(999L);
        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void cancelTask_succeeds() {
        doNothing().when(taskManager).cancel(1L);
        var response = controller.cancelTask(1L);
        assertEquals(200, response.getStatusCode().value());
        verify(taskManager).cancel(1L);
    }
}