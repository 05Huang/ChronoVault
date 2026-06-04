package com.chronovault.controller;

import com.chronovault.dto.branch.CreateBranchRequest;
import com.chronovault.dto.branch.MergeBranchRequest;
import com.chronovault.dto.branch.RenameBranchRequest;
import com.chronovault.dto.branch.ServerBranchDTO;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.security.SecurityUtils;
import com.chronovault.service.ServerBranchService;
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
@RequestMapping(ApiVersion.V1 + "/servers/{serverId}/branches")
@RequiredArgsConstructor
public class ServerBranchController {

    private final ServerBranchService branchService;
    private final UserService userService;

    @Operation(summary = "获取 Branches")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ServerBranchDTO>>> getBranches(@PathVariable Long serverId) {
        return ResponseEntity.ok(ApiResponse.success(branchService.getBranches(serverId)));
    }

    @Operation(summary = "操作 Branch")
    @PostMapping
    public ResponseEntity<ApiResponse<ServerBranchDTO>> createBranch(
            Authentication auth,
            @PathVariable Long serverId,
            @Valid @RequestBody CreateBranchRequest request) {
        Long userId = userService.getByEmail(SecurityUtils.getCurrentUsername(auth)).getId();
        ServerBranchDTO branch = branchService.createBranch(serverId, request, userId);
        return ResponseEntity.created(URI.create(ApiVersion.V1 + "servers/" + serverId + "/branches/" + branch.id()))
                .body(ApiResponse.success(branch));
    }

    @Operation(summary = "删除 Branch")
    @DeleteMapping("/{branchId}")
    public ResponseEntity<ApiResponse<Void>> deleteBranch(
            @PathVariable Long serverId,
            @PathVariable Long branchId) {
        branchService.deleteBranch(serverId, branchId);
        return ResponseEntity.ok(ApiResponse.successMsg("分支已删除"));
    }

    @Operation(summary = "操作 switch Branch")
    @PostMapping("/{branchId}/switch")
    public ResponseEntity<ApiResponse<ServerBranchDTO>> switchBranch(
            Authentication auth,
            @PathVariable Long serverId,
            @PathVariable Long branchId) {
        Long userId = userService.getByEmail(SecurityUtils.getCurrentUsername(auth)).getId();
        return ResponseEntity.ok(ApiResponse.success(branchService.switchBranch(serverId, branchId, userId)));
    }

    @Operation(summary = "操作 merge Branches")
    @PostMapping("/merge")
    public ResponseEntity<ApiResponse<ServerBranchDTO>> mergeBranches(
            Authentication auth,
            @PathVariable Long serverId,
            @Valid @RequestBody MergeBranchRequest request) {
        Long userId = userService.getByEmail(SecurityUtils.getCurrentUsername(auth)).getId();
        return ResponseEntity.ok(ApiResponse.success(branchService.mergeBranches(serverId, request, userId)));
    }

    @Operation(summary = "更新 Branch")
    @PutMapping("/{branchId}")
    public ResponseEntity<ApiResponse<ServerBranchDTO>> renameBranch(
            @PathVariable Long serverId,
            @PathVariable Long branchId,
            @Valid @RequestBody RenameBranchRequest body) {
        return ResponseEntity.ok()
                .location(URI.create(ApiVersion.V1 + "servers/" + serverId + "/branches/" + branchId))
                .body(ApiResponse.success(
                        branchService.renameBranch(serverId, branchId, body.name())));
    }
}
