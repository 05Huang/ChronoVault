package com.chronovault.controller;

import com.chronovault.audit.Auditable;
import com.chronovault.dto.team.InviteRequest;
import com.chronovault.dto.team.TeamMemberDTO;
import com.chronovault.dto.team.UpdateMemberRequest;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.security.SecurityUtils;
import com.chronovault.service.TeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import com.chronovault.config.ApiVersion;

@RestController
@RequestMapping(ApiVersion.V1 + "/team")
@RequiredArgsConstructor
@Tag(name = "Team", description = "团队管理 — 成员邀请、角色变更")
public class TeamController {

    private final TeamService teamService;

    @Operation(summary = "获取 Members")
    @GetMapping
    public ResponseEntity<ApiResponse<List<TeamMemberDTO>>> getMembers(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(teamService.getMembers(SecurityUtils.getCurrentUsername(auth))));
    }

    @Auditable(action = "邀请成员", changeType = "USER_ACTION", resourceType = "USER")
    @Operation(summary = "操作 invite")
    @PostMapping("/invite")
    public ResponseEntity<ApiResponse<TeamMemberDTO>> invite(Authentication auth, @Valid @RequestBody InviteRequest request) {
        TeamMemberDTO member = teamService.invite(SecurityUtils.getCurrentUsername(auth), request);
        return ResponseEntity.created(URI.create(ApiVersion.V1 + "team/" + member.id()))
                .body(ApiResponse.success(member));
    }

    @Auditable(action = "更新成员角色", changeType = "USER_ACTION", resourceType = "USER", resourceId = "#id")
    @Operation(summary = "更新 Member")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TeamMemberDTO>> updateMember(@PathVariable Long id, @RequestBody UpdateMemberRequest request) {
        return ResponseEntity.ok()
                .location(URI.create(ApiVersion.V1 + "team/" + id))
                .body(ApiResponse.success(teamService.updateMember(id, request)));
    }

    @Auditable(action = "移除成员", changeType = "USER_ACTION", resourceType = "USER", resourceId = "#id")
    @Operation(summary = "删除 Member")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> removeMember(@PathVariable Long id) {
        teamService.removeMember(id);
        return ResponseEntity.ok(ApiResponse.successMsg("成员已移除"));
    }
}
