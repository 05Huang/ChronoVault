package com.chronovault.service;

import com.chronovault.dto.team.InviteRequest;
import com.chronovault.dto.team.TeamMemberDTO;
import com.chronovault.dto.team.UpdateMemberRequest;
import com.chronovault.entity.TeamMember;
import com.chronovault.entity.User;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.TeamMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamMemberRepository teamMemberRepository;
    private final UserService userService;

    public List<TeamMemberDTO> getMembers(String email) {
        User user = userService.getByEmail(email);
        return teamMemberRepository.findByOwnerId(user.getId()).stream()
                .map(TeamMemberDTO::from)
                .toList();
    }

    @Transactional
    public TeamMemberDTO invite(String email, InviteRequest request) {
        User owner = userService.getByEmail(email);
        User.Role role = request.role() != null ? User.Role.valueOf(request.role()) : User.Role.MEMBER;
        String permissions = request.permissions() != null ? request.permissions() : getDefaultPermissions(role);
        TeamMember member = TeamMember.builder()
                .owner(owner)
                .name(request.name())
                .email(request.email())
                .role(role)
                .permissions(permissions)
                .status(TeamMember.MemberStatus.PENDING)
                .build();
        teamMemberRepository.save(member);
        return TeamMemberDTO.from(member);
    }

    private String getDefaultPermissions(User.Role role) {
        return switch (role) {
            case OWNER, ADMIN -> "快照管理,恢复操作,服务器管理,存储管理,团队管理,设置管理";
            case MEMBER -> "快照管理,恢复操作";
            case VIEWER -> "只读访问";
        };
    }

    @Transactional
    public TeamMemberDTO updateMember(Long id, UpdateMemberRequest request) {
        TeamMember member = teamMemberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("成员不存在: " + id));
        if (request.role() != null) member.setRole(User.Role.valueOf(request.role()));
        if (request.permissions() != null) member.setPermissions(request.permissions());
        teamMemberRepository.save(member);
        return TeamMemberDTO.from(member);
    }

    @Transactional
    public void removeMember(Long id) {
        TeamMember member = teamMemberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("成员不存在: " + id));
        teamMemberRepository.delete(member);
    }
}
