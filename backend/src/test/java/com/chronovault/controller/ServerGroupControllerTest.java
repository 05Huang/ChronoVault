package com.chronovault.controller;

import com.chronovault.entity.ServerGroup;
import com.chronovault.entity.User;
import com.chronovault.service.ServerGroupService;
import com.chronovault.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServerGroupControllerTest {

    @Mock private ServerGroupService groupService;
    @Mock private UserService userService;

    @InjectMocks
    private ServerGroupController controller;

    @Test
    void getGroups_returnsList() {
        User user = User.builder().id(1L).email("test@test.com").build();
        when(userService.getByEmail("test@test.com")).thenReturn(user);
        when(groupService.getGroups(1L)).thenReturn(List.of());
        var auth = new UsernamePasswordAuthenticationToken("test@test.com", null, List.of());
        var response = controller.getGroups(auth);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void createGroup_succeeds() {
        User user = User.builder().id(1L).email("test@test.com").build();
        when(userService.getByEmail("test@test.com")).thenReturn(user);
        ServerGroup group = ServerGroup.builder().id(1L).name("Production").build();
        when(groupService.createGroup(eq(1L), any(ServerGroup.class))).thenReturn(group);
        var auth = new UsernamePasswordAuthenticationToken("test@test.com", null, List.of());
        var response = controller.createGroup(auth, new ServerGroup());
        assertEquals(201, response.getStatusCode().value());
    }

    @Test
    void updateGroup_succeeds() {
        ServerGroup group = ServerGroup.builder().id(1L).name("Updated").build();
        when(groupService.updateGroup(eq(1L), any(ServerGroup.class))).thenReturn(group);
        var response = controller.updateGroup(1L, new ServerGroup());
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void deleteGroup_succeeds() {
        doNothing().when(groupService).deleteGroup(1L);
        var response = controller.deleteGroup(1L);
        assertEquals(200, response.getStatusCode().value());
        verify(groupService).deleteGroup(1L);
    }

    @Test
    void addServerToGroup_succeeds() {
        doNothing().when(groupService).addServerToGroup(1L, 1L);
        var response = controller.addServerToGroup(1L, 1L);
        assertEquals(200, response.getStatusCode().value());
        verify(groupService).addServerToGroup(1L, 1L);
    }

    @Test
    void removeServerFromGroup_succeeds() {
        doNothing().when(groupService).removeServerFromGroup(1L);
        var response = controller.removeServerFromGroup(1L);
        assertEquals(200, response.getStatusCode().value());
        verify(groupService).removeServerFromGroup(1L);
    }
}