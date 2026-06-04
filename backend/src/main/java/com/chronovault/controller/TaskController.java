package com.chronovault.controller;

import com.chronovault.entity.AsyncTask;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.repository.AsyncTaskRepository;
import com.chronovault.task.AsyncTaskManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final AsyncTaskRepository taskRepository;
    private final AsyncTaskManager taskManager;

    @GetMapping
    public ResponseEntity<?> getTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Use paginated query to prevent OOM
        var result = taskRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(page, Math.min(size, 100)));
        return ResponseEntity.ok(ApiResponse.successPage(
                result.getContent(), page, size, result.getTotalElements()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AsyncTask>> getTask(@PathVariable Long id) {
        AsyncTask task = taskManager.getStatus(id);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.success(task));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelTask(@PathVariable Long id) {
        taskManager.cancel(id);
        return ResponseEntity.ok(ApiResponse.successMsg("任务已取消"));
    }
}
