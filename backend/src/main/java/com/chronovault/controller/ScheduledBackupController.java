package com.chronovault.controller;

import com.chronovault.dto.scheduledbackup.CreateScheduledBackupRequest;
import com.chronovault.dto.scheduledbackup.ScheduledBackupDTO;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.service.ScheduledBackupService;
import com.chronovault.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/scheduled-backups")
@RequiredArgsConstructor
public class ScheduledBackupController {

    private final ScheduledBackupService scheduledBackupService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ScheduledBackupDTO>>> getAll(Authentication auth) {
        Long userId = userService.getByEmail(auth.getName()).getId();
        return ResponseEntity.ok(ApiResponse.success(scheduledBackupService.getAll(userId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ScheduledBackupDTO>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(scheduledBackupService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ScheduledBackupDTO>> create(
            Authentication auth,
            @Valid @RequestBody CreateScheduledBackupRequest request) {
        Long userId = userService.getByEmail(auth.getName()).getId();
        return ResponseEntity.ok(ApiResponse.success(scheduledBackupService.create(request, userId)));
    }

    @PutMapping("/{id}/toggle")
    public ResponseEntity<ApiResponse<ScheduledBackupDTO>> toggle(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(scheduledBackupService.toggleEnabled(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        scheduledBackupService.delete(id);
        return ResponseEntity.ok(ApiResponse.successMsg("定时备份已删除"));
    }
}
