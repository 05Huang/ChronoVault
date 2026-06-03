package com.chronovault.controller;

import com.chronovault.dto.branch.CreateBranchRequest;
import com.chronovault.dto.branch.MergeBranchRequest;
import com.chronovault.dto.branch.RenameBranchRequest;
import com.chronovault.dto.branch.ServerBranchDTO;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.service.ServerBranchService;
import com.chronovault.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/servers/{serverId}/branches")
@RequiredArgsConstructor
public class ServerBranchController {

    private final ServerBranchService branchService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ServerBranchDTO>>> getBranches(@PathVariable Long serverId) {
        return ResponseEntity.ok(ApiResponse.success(branchService.getBranches(serverId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ServerBranchDTO>> createBranch(
            Authentication auth,
            @PathVariable Long serverId,
            @Valid @RequestBody CreateBranchRequest request) {
        Long userId = userService.getByEmail(auth.getName()).getId();
        return ResponseEntity.ok(ApiResponse.success(branchService.createBranch(serverId, request, userId)));
    }

    @DeleteMapping("/{branchId}")
    public ResponseEntity<ApiResponse<Void>> deleteBranch(
            @PathVariable Long serverId,
            @PathVariable Long branchId) {
        branchService.deleteBranch(serverId, branchId);
        return ResponseEntity.ok(ApiResponse.successMsg("分支已删除"));
    }

    @PostMapping("/{branchId}/switch")
    public ResponseEntity<ApiResponse<ServerBranchDTO>> switchBranch(
            Authentication auth,
            @PathVariable Long serverId,
            @PathVariable Long branchId) {
        Long userId = userService.getByEmail(auth.getName()).getId();
        return ResponseEntity.ok(ApiResponse.success(branchService.switchBranch(serverId, branchId, userId)));
    }

    @PostMapping("/merge")
    public ResponseEntity<ApiResponse<ServerBranchDTO>> mergeBranches(
            Authentication auth,
            @PathVariable Long serverId,
            @Valid @RequestBody MergeBranchRequest request) {
        Long userId = userService.getByEmail(auth.getName()).getId();
        return ResponseEntity.ok(ApiResponse.success(branchService.mergeBranches(serverId, request, userId)));
    }

    @PutMapping("/{branchId}")
    public ResponseEntity<ApiResponse<ServerBranchDTO>> renameBranch(
            @PathVariable Long serverId,
            @PathVariable Long branchId,
            @Valid @RequestBody RenameBranchRequest body) {
        return ResponseEntity.ok(ApiResponse.success(
                branchService.renameBranch(serverId, branchId, body.name())));
    }
}
