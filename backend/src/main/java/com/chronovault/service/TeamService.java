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
        TeamMember member = TeamMember.builder()
                .owner(owner)
                .name(request.name())
                .email(request.email())
                .role(request.role() != null ? User.Role.valueOf(request.role()) : User.Role.MEMBER)
                .permissions("快照管理,恢复操作")
                .status(TeamMember.MemberStatus.PENDING)
                .build();
        teamMemberRepository.save(member);
        return TeamMemberDTO.from(member);
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
}
