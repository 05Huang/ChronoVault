package com.chronovault.controller;

import com.chronovault.entity.SnapshotHook;
import com.chronovault.service.SnapshotHookService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SnapshotHookControllerTest {

    @Mock private SnapshotHookService hookService;

    @InjectMocks
    private SnapshotHookController controller;

    @Test
    void getHooks_returnsList() {
        when(hookService.getHooks(1L)).thenReturn(List.of());
        var response = controller.getHooks(1L);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void createHook_succeeds() {
        SnapshotHook hook = SnapshotHook.builder().id(1L).name("test").command("ls").build();
        when(hookService.createHook(eq(1L), any(SnapshotHook.class))).thenReturn(hook);
        var response = controller.createHook(1L, new SnapshotHook());
        assertEquals(201, response.getStatusCode().value());
    }

    @Test
    void updateHook_succeeds() {
        SnapshotHook hook = SnapshotHook.builder().id(1L).name("updated").build();
        when(hookService.updateHook(eq(1L), eq(1L), any(SnapshotHook.class))).thenReturn(hook);
        var response = controller.updateHook(1L, 1L, new SnapshotHook());
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void deleteHook_succeeds() {
        doNothing().when(hookService).deleteHook(1L, 1L);
        var response = controller.deleteHook(1L, 1L);
        assertEquals(200, response.getStatusCode().value());
        verify(hookService).deleteHook(1L, 1L);
    }
}