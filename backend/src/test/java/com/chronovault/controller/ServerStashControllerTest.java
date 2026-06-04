package com.chronovault.controller;

import com.chronovault.dto.snapshot.SnapshotDTO;
import com.chronovault.dto.stash.CreateStashRequest;
import com.chronovault.entity.User;
import com.chronovault.service.SnapshotStashService;
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
class ServerStashControllerTest {

    @Mock private SnapshotStashService stashService;
    @Mock private UserService userService;

    @InjectMocks
    private ServerStashController controller;

    @Test
    void createStash_succeeds() {
        User user = User.builder().id(1L).email("test@test.com").build();
        when(userService.getByEmail("test@test.com")).thenReturn(user);
        SnapshotDTO stash = new SnapshotDTO(1L, "stash", "2026-01-01T00:00:00", "STABLE", "test", null, 0, "server", 0L, null, List.of(), null, null);
        when(stashService.createStash(eq(1L), any(), eq(1L))).thenReturn(stash);
        var auth = new UsernamePasswordAuthenticationToken("test@test.com", null, List.of());
        var response = controller.createStash(auth, 1L, null);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void listStashes_returnsList() {
        when(stashService.listStashes(1L)).thenReturn(List.of());
        var response = controller.listStashes(1L);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void popStash_succeeds() {
        User user = User.builder().id(1L).email("test@test.com").build();
        when(userService.getByEmail("test@test.com")).thenReturn(user);
        when(stashService.popStash(1L, 1L)).thenReturn("Stash popped");
        var auth = new UsernamePasswordAuthenticationToken("test@test.com", null, List.of());
        var response = controller.popStash(auth, 1L);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void discardStash_succeeds() {
        doNothing().when(stashService).discardStash(1L, 1L);
        var response = controller.discardStash(1L, 1L);
        assertEquals(200, response.getStatusCode().value());
        verify(stashService).discardStash(1L, 1L);
    }
}