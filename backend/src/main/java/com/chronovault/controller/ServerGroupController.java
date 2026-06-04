package com.chronovault.controller;

import com.chronovault.entity.ServerGroup;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.security.SecurityUtils;
import com.chronovault.service.ServerGroupService;
import com.chronovault.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import com.chronovault.config.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping(ApiVersion.V1 + "/server-groups")
@RequiredArgsConstructor
public class ServerGroupController {

    private final ServerGroupService groupService;
    private final UserService userService;

    @Operation(summary = "获取 Groups")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ServerGroup>>> getGroups(Authentication auth) {
        Long userId = userService.getByEmail(SecurityUtils.getCurrentUsername(auth)).getId();
        return ResponseEntity.ok(ApiResponse.success(groupService.getGroups(userId)));
    }

    @Operation(summary = "操作 Group")
    @PostMapping
    public ResponseEntity<ApiResponse<ServerGroup>> createGroup(
            Authentication auth,
            @RequestBody ServerGroup group) {
        Long userId = userService.getByEmail(SecurityUtils.getCurrentUsername(auth)).getId();
        ServerGroup created = groupService.createGroup(userId, group);
        return ResponseEntity.created(URI.create(ApiVersion.V1 + "server-groups/" + created.getId()))
                .body(ApiResponse.success(created));
    }

    @Operation(summary = "更新 Group")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ServerGroup>> updateGroup(
            @PathVariable Long id,
            @RequestBody ServerGroup group) {
        return ResponseEntity.ok()
                .location(URI.create(ApiVersion.V1 + "server-groups/" + id))
                .body(ApiResponse.success(groupService.updateGroup(id, group)));
    }

    @Operation(summary = "删除 Group")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteGroup(@PathVariable Long id) {
        groupService.deleteGroup(id);
        return ResponseEntity.ok(ApiResponse.successMsg("分组已删除"));
    }

    @Operation(summary = "操作 Server To Group")
    @PostMapping("/{groupId}/servers/{serverId}")
    public ResponseEntity<ApiResponse<Void>> addServerToGroup(
            @PathVariable Long groupId,
            @PathVariable Long serverId) {
        groupService.addServerToGroup(groupId, serverId);
        return ResponseEntity.ok()
                .location(URI.create(ApiVersion.V1 + "server-groups/" + groupId))
                .body(ApiResponse.successMsg("服务器已添加到分组"));
    }

    @Operation(summary = "删除 Server From Group")
    @DeleteMapping("/servers/{serverId}")
    public ResponseEntity<ApiResponse<Void>> removeServerFromGroup(@PathVariable Long serverId) {
        groupService.removeServerFromGroup(serverId);
        return ResponseEntity.ok(ApiResponse.successMsg("服务器已从分组移除"));
    }
}