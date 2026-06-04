package com.chronovault.controller;

import com.chronovault.dto.team.*;
import com.chronovault.service.TeamService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamControllerTest {

    @Mock
    private TeamService teamService;

    @InjectMocks
    private TeamController controller;

    private Authentication auth() {
        return new UsernamePasswordAuthenticationToken("test@test.com", null);
    }

    @Test
    void getMembers_returnsList() {
        when(teamService.getMembers("test@test.com")).thenReturn(List.of(
                new TeamMemberDTO(1L, "Test User", "test@test.com", "OWNER", "T", "ACTIVE", "")));

        var response = controller.getMembers(auth());
        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().data().size());
        assertEquals("OWNER", response.getBody().data().get(0).role());
    }

    @Test
    void invite_validRequest_succeeds() {
        InviteRequest request = new InviteRequest("New User", "new@test.com", "MEMBER", null);
        when(teamService.invite("test@test.com", request))
                .thenReturn(new TeamMemberDTO(2L, "New User", "new@test.com", "MEMBER", "N", "PENDING", ""));

        var response = controller.invite(auth(), request);
        assertEquals(201, response.getStatusCode().value());
        assertEquals("new@test.com", response.getBody().data().email());
    }

    @Test
    void updateMember_validRequest_succeeds() {
        UpdateMemberRequest request = new UpdateMemberRequest("ADMIN", null);
        when(teamService.updateMember(1L, request))
                .thenReturn(new TeamMemberDTO(1L, "Test User", "test@test.com", "ADMIN", "T", "ACTIVE", ""));

        var response = controller.updateMember(1L, request);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("ADMIN", response.getBody().data().role());
    }

    @Test
    void removeMember_validId_succeeds() {
        doNothing().when(teamService).removeMember(1L);

        var response = controller.removeMember(1L);
        assertEquals(200, response.getStatusCode().value());
        verify(teamService).removeMember(1L);
    }
}