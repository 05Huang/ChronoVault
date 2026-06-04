package com.chronovault.service;

import com.chronovault.entity.Server;
import com.chronovault.entity.SnapshotHook;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.ServerRepository;
import com.chronovault.repository.SnapshotHookRepository;
import com.chronovault.ssh.SshConnectionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SnapshotHookServiceTest {

    @Mock private SnapshotHookRepository hookRepository;
    @Mock private ServerRepository serverRepository;
    @Mock private SshConnectionManager sshManager;

    @InjectMocks
    private SnapshotHookService service;

    @Test
    void getHooks_returnsList() {
        when(hookRepository.findByServerIdOrderByOrderIndexAsc(1L)).thenReturn(List.of());
        var result = service.getHooks(1L);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void createHook_serverNotFound_throwsException() {
        when(serverRepository.findById(999L)).thenReturn(java.util.Optional.empty());
        SnapshotHook hook = SnapshotHook.builder().name("test").command("ls").build();
        assertThrows(ResourceNotFoundException.class, () -> service.createHook(999L, hook));
    }

    @Test
    void createHook_serverFound_savesAndReturns() {
        Server server = Server.builder().id(1L).name("test-server").build();
        when(serverRepository.findById(1L)).thenReturn(Optional.of(server));
        when(hookRepository.save(any(SnapshotHook.class))).thenAnswer(inv -> inv.getArgument(0));
        SnapshotHook hook = SnapshotHook.builder().name("test").command("ls").build();

        var result = service.createHook(1L, hook);
        assertNotNull(result);
        assertEquals(server, result.getServer());
    }
}