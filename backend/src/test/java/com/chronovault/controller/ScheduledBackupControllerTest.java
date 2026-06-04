package com.chronovault.controller;

import com.chronovault.dto.scheduledbackup.CreateScheduledBackupRequest;
import com.chronovault.dto.scheduledbackup.ScheduledBackupDTO;
import com.chronovault.entity.User;
import com.chronovault.service.ScheduledBackupService;
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
class ScheduledBackupControllerTest {

    @Mock private ScheduledBackupService scheduledBackupService;
    @Mock private UserService userService;

    @InjectMocks
    private ScheduledBackupController controller;

    @Test
    void getAll_returnsList() {
        User user = User.builder().id(1L).email("test@test.com").build();
        when(userService.getByEmail("test@test.com")).thenReturn(user);
        when(scheduledBackupService.getAll(1L)).thenReturn(List.of());
        var auth = new UsernamePasswordAuthenticationToken("test@test.com", null, List.of());
        var response = controller.getAll(auth);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void getById_returnsBackup() {
        ScheduledBackupDTO backup = new ScheduledBackupDTO(1L, "Daily", 1L, "server1", null, "0 2 * * *", true, "/", null, null, null, "SUCCESS", null, 0, "2026-01-01T00:00:00");
        when(scheduledBackupService.getById(1L)).thenReturn(backup);
        var response = controller.getById(1L);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void create_succeeds() {
        User user = User.builder().id(1L).email("test@test.com").build();
        when(userService.getByEmail("test@test.com")).thenReturn(user);
        ScheduledBackupDTO backup = new ScheduledBackupDTO(1L, "Daily", 1L, "server1", null, "0 2 * * *", true, "/", null, null, null, "SUCCESS", null, 0, "2026-01-01T00:00:00");
        when(scheduledBackupService.create(any(CreateScheduledBackupRequest.class), eq(1L))).thenReturn(backup);
        var auth = new UsernamePasswordAuthenticationToken("test@test.com", null, List.of());
        CreateScheduledBackupRequest request = new CreateScheduledBackupRequest(1L, 1L, "Daily", "0 2 * * *", "/", null);
        var response = controller.create(auth, request);
        assertEquals(201, response.getStatusCode().value());
    }

    @Test
    void delete_succeeds() {
        doNothing().when(scheduledBackupService).delete(1L);
        var response = controller.delete(1L);
        assertEquals(200, response.getStatusCode().value());
        verify(scheduledBackupService).delete(1L);
    }

    @Test
    void toggle_succeeds() {
        ScheduledBackupDTO backup = new ScheduledBackupDTO(1L, "Daily", 1L, "server1", null, "0 2 * * *", false, "/", null, null, null, "SUCCESS", null, 0, "2026-01-01T00:00:00");
        when(scheduledBackupService.toggleEnabled(1L)).thenReturn(backup);
        var response = controller.toggle(1L);
        assertEquals(200, response.getStatusCode().value());
    }
}