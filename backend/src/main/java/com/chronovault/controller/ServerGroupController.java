package com.chronovault.controller;

import com.chronovault.entity.ServerGroup;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.service.ServerGroupService;
import com.chronovault.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/server-groups")
@RequiredArgsConstructor
public class ServerGroupController {

    private final ServerGroupService groupService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ServerGroup>>> getGroups(Authentication auth) {
        Long userId = userService.getByEmail(auth.getName()).getId();
        return ResponseEntity.ok(ApiResponse.success(groupService.getGroups(userId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ServerGroup>> createGroup(
            Authentication auth,
            @RequestBody ServerGroup group) {
        Long userId = userService.getByEmail(auth.getName()).getId();
        return ResponseEntity.ok(ApiResponse.success(groupService.createGroup(userId, group)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ServerGroup>> updateGroup(
            @PathVariable Long id,
            @RequestBody ServerGroup group) {
        return ResponseEntity.ok(ApiResponse.success(groupService.updateGroup(id, group)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteGroup(@PathVariable Long id) {
        groupService.deleteGroup(id);
        return ResponseEntity.ok(ApiResponse.successMsg("分组已删除"));
    }

    @PostMapping("/{groupId}/servers/{serverId}")
    public ResponseEntity<ApiResponse<Void>> addServerToGroup(
            @PathVariable Long groupId,
            @PathVariable Long serverId) {
        groupService.addServerToGroup(groupId, serverId);
        return ResponseEntity.ok(ApiResponse.successMsg("服务器已添加到分组"));
    }

    @DeleteMapping("/servers/{serverId}")
    public ResponseEntity<ApiResponse<Void>> removeServerFromGroup(@PathVariable Long serverId) {
        groupService.removeServerFromGroup(serverId);
        return ResponseEntity.ok(ApiResponse.successMsg("服务器已从分组移除"));
    }
}