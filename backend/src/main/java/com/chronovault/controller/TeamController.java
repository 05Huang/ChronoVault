package com.chronovault.controller;

import com.chronovault.dto.team.InviteRequest;
import com.chronovault.dto.team.TeamMemberDTO;
import com.chronovault.dto.team.UpdateMemberRequest;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.service.TeamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/team")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TeamMemberDTO>>> getMembers(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(teamService.getMembers(auth.getName())));
    }

    @PostMapping("/invite")
    public ResponseEntity<ApiResponse<TeamMemberDTO>> invite(Authentication auth, @Valid @RequestBody InviteRequest request) {
        return ResponseEntity.ok(ApiResponse.success(teamService.invite(auth.getName(), request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TeamMemberDTO>> updateMember(@PathVariable Long id, @RequestBody UpdateMemberRequest request) {
        return ResponseEntity.ok(ApiResponse.success(teamService.updateMember(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> removeMember(@PathVariable Long id) {
        teamService.removeMember(id);
        return ResponseEntity.ok(ApiResponse.successMsg("成员已移除"));
    }
}
