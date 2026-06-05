package com.chronovault.service;

import com.chronovault.entity.AuditLog;
import com.chronovault.entity.Server;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.AuditLogRepository;
import com.chronovault.repository.ServerRepository;
import com.chronovault.repository.SnapshotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChangeAttributionServiceTest {

    @Mock private AuditLogRepository auditLogRepository;
    @Mock private ServerRepository serverRepository;
    @Mock private SnapshotRepository snapshotRepository;

    @InjectMocks
    private ChangeAttributionService service;

    @Test
    void getServerBlame_serverNotFound_throwsException() {
        when(serverRepository.findById(999L)).thenReturn(java.util.Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.getServerBlame(999L));
    }

    @Test
    void getServerBlame_withLogs_returnsList() {
        Server server = Server.builder().id(1L).name("test-server").build();
        when(serverRepository.findById(1L)).thenReturn(Optional.of(server));
        when(auditLogRepository.findByServerId(eq(1L), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));

        var result = service.getServerBlame(1L);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}