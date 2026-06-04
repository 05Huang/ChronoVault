package com.chronovault.controller;

import com.chronovault.audit.Auditable;
import com.chronovault.dto.scheduledbackup.CreateScheduledBackupRequest;
import com.chronovault.dto.scheduledbackup.ScheduledBackupDTO;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.security.SecurityUtils;
import com.chronovault.service.ScheduledBackupService;
import com.chronovault.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import com.chronovault.config.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping(ApiVersion.V1 + "/scheduled-backups")
@RequiredArgsConstructor
public class ScheduledBackupController {

    private final ScheduledBackupService scheduledBackupService;
    private final UserService userService;

    @Operation(summary = "获取 All")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ScheduledBackupDTO>>> getAll(Authentication auth) {
        Long userId = userService.getByEmail(SecurityUtils.getCurrentUsername(auth)).getId();
        return ResponseEntity.ok(ApiResponse.success(scheduledBackupService.getAll(userId)));
    }

    @Operation(summary = "获取 By Id")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ScheduledBackupDTO>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(scheduledBackupService.getById(id)));
    }

    @Auditable(action = "创建定时备份", changeType = "CONFIG_CHANGED", resourceType = "SCHEDULED_BACKUP")
    @Operation(summary = "操作 create")
    @PostMapping
    public ResponseEntity<ApiResponse<ScheduledBackupDTO>> create(
            Authentication auth,
            @Valid @RequestBody CreateScheduledBackupRequest request) {
        Long userId = userService.getByEmail(SecurityUtils.getCurrentUsername(auth)).getId();
        ScheduledBackupDTO backup = scheduledBackupService.create(request, userId);
        return ResponseEntity.created(URI.create(ApiVersion.V1 + "scheduled-backups/" + backup.id()))
                .body(ApiResponse.success(backup));
    }

    @Auditable(action = "切换定时备份状态", changeType = "CONFIG_CHANGED", resourceType = "SCHEDULED_BACKUP", resourceId = "#id")
    @Operation(summary = "更新 toggle")
    @PutMapping("/{id}/toggle")
    public ResponseEntity<ApiResponse<ScheduledBackupDTO>> toggle(@PathVariable Long id) {
        return ResponseEntity.ok()
                .location(URI.create(ApiVersion.V1 + "scheduled-backups/" + id))
                .body(ApiResponse.success(scheduledBackupService.toggleEnabled(id)));
    }

    @Auditable(action = "删除定时备份", changeType = "CONFIG_CHANGED", resourceType = "SCHEDULED_BACKUP", resourceId = "#id")
    @Operation(summary = "删除 delete")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        scheduledBackupService.delete(id);
        return ResponseEntity.ok(ApiResponse.successMsg("定时备份已删除"));
    }
}
