package com.chronovault.controller;

import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.entity.SnapshotRetentionPolicy;
import com.chronovault.entity.User;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.ServerRepository;
import com.chronovault.repository.UserRepository;
import com.chronovault.security.SecurityUtils;
import com.chronovault.service.SnapshotRetentionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;
import com.chronovault.config.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping(ApiVersion.V1 + "/retention-policies")
@RequiredArgsConstructor
public class RetentionPolicyController {

    private final SnapshotRetentionService retentionService;
    private final ServerRepository serverRepository;
    private final UserRepository userRepository;

    public record CreateRetentionPolicyRequest(
            @NotNull Long serverId,
            @NotBlank String name,
            Integer maxCount,
            Integer maxAgeDays,
            Integer minKeepDays
    ) {}

    @Operation(summary = "获取 All")
    @GetMapping
    public ResponseEntity<ApiResponse<List<SnapshotRetentionPolicy>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(retentionService.getAllPolicies()));
    }

    @Operation(summary = "获取 By Server")
    @GetMapping("/server/{serverId}")
    public ResponseEntity<ApiResponse<List<SnapshotRetentionPolicy>>> getByServer(@PathVariable Long serverId) {
        return ResponseEntity.ok(ApiResponse.success(retentionService.getPoliciesByServer(serverId)));
    }

    @Operation(summary = "操作 create")
    @PostMapping
    public ResponseEntity<ApiResponse<SnapshotRetentionPolicy>> create(
            @Valid @RequestBody CreateRetentionPolicyRequest request, Authentication auth) {
        User user = userRepository.findByEmail(SecurityUtils.getCurrentUsername(auth))
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在"));

        SnapshotRetentionPolicy policy = SnapshotRetentionPolicy.builder()
                .server(serverRepository.findById(request.serverId())
                        .orElseThrow(() -> new ResourceNotFoundException("服务器不存在")))
                .user(user)
                .name(request.name())
                .maxCount(request.maxCount())
                .maxAgeDays(request.maxAgeDays())
                .minKeepDays(request.minKeepDays() != null ? request.minKeepDays() : 7)
                .build();

        SnapshotRetentionPolicy created = retentionService.createPolicy(policy);
        return ResponseEntity.created(URI.create(ApiVersion.V1 + "retention-policies/" + created.getId()))
                .body(ApiResponse.success(created));
    }

    @Operation(summary = "更新 toggle")
    @PutMapping("/{id}/toggle")
    public ResponseEntity<ApiResponse<SnapshotRetentionPolicy>> toggle(@PathVariable Long id) {
        return ResponseEntity.ok()
                .location(URI.create(ApiVersion.V1 + "retention-policies/" + id))
                .body(ApiResponse.success(retentionService.togglePolicy(id)));
    }

    @Operation(summary = "删除 delete")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        retentionService.deletePolicy(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{id}/dry-run")
    public ResponseEntity<ApiResponse<Map<String, Object>>> dryRun(@PathVariable Long id) {
        List<Long> snapshotIds = retentionService.dryRunRetention(id);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "snapshotIds", snapshotIds,
                "count", snapshotIds.size(),
                "message", "将删除 " + snapshotIds.size() + " 个快照"
        )));
    }
}
