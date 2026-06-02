package com.chronovault.controller;

import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.entity.SnapshotRetentionPolicy;
import com.chronovault.entity.User;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.ServerRepository;
import com.chronovault.repository.UserRepository;
import com.chronovault.service.SnapshotRetentionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/retention-policies")
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

    @GetMapping
    public ResponseEntity<ApiResponse<List<SnapshotRetentionPolicy>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(retentionService.getAllPolicies()));
    }

    @GetMapping("/server/{serverId}")
    public ResponseEntity<ApiResponse<List<SnapshotRetentionPolicy>>> getByServer(@PathVariable Long serverId) {
        return ResponseEntity.ok(ApiResponse.success(retentionService.getPoliciesByServer(serverId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SnapshotRetentionPolicy>> create(
            @Valid @RequestBody CreateRetentionPolicyRequest request, Authentication auth) {
        User user = userRepository.findByEmail(auth.getName())
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

        return ResponseEntity.ok(ApiResponse.success(retentionService.createPolicy(policy)));
    }

    @PutMapping("/{id}/toggle")
    public ResponseEntity<ApiResponse<SnapshotRetentionPolicy>> toggle(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(retentionService.togglePolicy(id)));
    }

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
