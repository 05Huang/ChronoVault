package com.chronovault.service;

import com.chronovault.dto.team.InviteRequest;
import com.chronovault.dto.team.TeamMemberDTO;
import com.chronovault.dto.team.UpdateMemberRequest;
import com.chronovault.entity.TeamMember;
import com.chronovault.entity.User;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.TeamMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private UserService userService;

    @InjectMocks
    private TeamService teamService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder().id(1L).name("Owner").email("owner@example.com").role(User.Role.OWNER).build();
    }

    @Test
    void getMembers_returnsTeamMembers() {
        TeamMember member = TeamMember.builder().id(1L).owner(testUser).name("John").email("john@example.com")
                .role(User.Role.MEMBER).permissions("快照管理").status(TeamMember.MemberStatus.PENDING).build();
        when(userService.getByEmail("owner@example.com")).thenReturn(testUser);
        when(teamMemberRepository.findByOwnerId(1L)).thenReturn(List.of(member));

        var result = teamService.getMembers("owner@example.com");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("John", result.get(0).name());
    }

    @Test
    void getMembers_emptyList() {
        when(userService.getByEmail("owner@example.com")).thenReturn(testUser);
        when(teamMemberRepository.findByOwnerId(1L)).thenReturn(List.of());

        var result = teamService.getMembers("owner@example.com");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void invite_validRequest_createsMember() {
        when(userService.getByEmail("owner@example.com")).thenReturn(testUser);
        when(teamMemberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InviteRequest request = new InviteRequest("New Member", "new@example.com", "MEMBER", null);
        TeamMemberDTO result = teamService.invite("owner@example.com", request);

        assertNotNull(result);
        assertEquals("New Member", result.name());
    }

    @Test
    void invite_defaultPermissions_appliesBasedOnRole() {
        when(userService.getByEmail("owner@example.com")).thenReturn(testUser);
        when(teamMemberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InviteRequest request = new InviteRequest("Viewer", "viewer@example.com", "VIEWER", null);
        TeamMemberDTO result = teamService.invite("owner@example.com", request);

        assertNotNull(result);
        assertEquals("Viewer", result.name());
    }

    @Test
    void removeMember_existingMember_deletes() {
        TeamMember member = TeamMember.builder().id(1L).owner(testUser).name("John").build();
        when(teamMemberRepository.findById(1L)).thenReturn(Optional.of(member));

        teamService.removeMember(1L);

        verify(teamMemberRepository).delete(member);
    }

    @Test
    void removeMember_nonExistingMember_throwsException() {
        when(teamMemberRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> teamService.removeMember(999L));
    }

    @Test
    void updateMember_validRequest_updatesRoleAndPermissions() {
        TeamMember member = TeamMember.builder().id(1L).owner(testUser).name("John")
                .role(User.Role.MEMBER).permissions("read")
                .status(TeamMember.MemberStatus.ONLINE).build();
        when(teamMemberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(teamMemberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateMemberRequest request = new UpdateMemberRequest("ADMIN", "full-access");
        TeamMemberDTO result = teamService.updateMember(1L, request);

        assertEquals(User.Role.ADMIN, member.getRole());
        assertEquals("full-access", member.getPermissions());
    }

    @Test
    void updateMember_partialUpdate_onlyUpdatesProvidedFields() {
        TeamMember member = TeamMember.builder().id(1L).owner(testUser).name("John")
                .role(User.Role.MEMBER).permissions("read")
                .status(TeamMember.MemberStatus.ONLINE).build();
        when(teamMemberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(teamMemberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateMemberRequest request = new UpdateMemberRequest("ADMIN", null);
        teamService.updateMember(1L, request);

        assertEquals(User.Role.ADMIN, member.getRole());
        assertEquals("read", member.getPermissions()); // unchanged
    }

    @Test
    void updateMember_nonExistingMember_throwsException() {
        when(teamMemberRepository.findById(999L)).thenReturn(Optional.empty());
        UpdateMemberRequest request = new UpdateMemberRequest("ADMIN", null);

        assertThrows(ResourceNotFoundException.class, () -> teamService.updateMember(999L, request));
    }

    @Test
    void invite_adminRole_appliesFullPermissions() {
        when(userService.getByEmail("owner@example.com")).thenReturn(testUser);
        when(teamMemberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InviteRequest request = new InviteRequest("Admin User", "admin@example.com", "ADMIN", null);
        TeamMemberDTO result = teamService.invite("owner@example.com", request);

        assertNotNull(result);
        assertEquals("Admin User", result.name());
    }

    @Test
    void invite_withCustomPermissions_usesProvidedPermissions() {
        when(userService.getByEmail("owner@example.com")).thenReturn(testUser);
        when(teamMemberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InviteRequest request = new InviteRequest("Custom", "custom@example.com", "MEMBER", "read-only,view-snapshots");
        TeamMemberDTO result = teamService.invite("owner@example.com", request);

        assertNotNull(result);
        assertEquals("Custom", result.name());
    }
}
