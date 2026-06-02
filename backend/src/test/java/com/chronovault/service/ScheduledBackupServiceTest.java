package com.chronovault.service;

import com.chronovault.dto.scheduledbackup.CreateScheduledBackupRequest;
import com.chronovault.dto.scheduledbackup.ScheduledBackupDTO;
import com.chronovault.entity.ScheduledBackup;
import com.chronovault.entity.Server;
import com.chronovault.entity.User;
import com.chronovault.exception.BadRequestException;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.ScheduledBackupRepository;
import com.chronovault.repository.ServerRepository;
import com.chronovault.repository.StorageTargetRepository;
import com.chronovault.repository.UserRepository;
import com.chronovault.snapshot.SnapshotEngine;
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
class ScheduledBackupServiceTest {

    @Mock private ScheduledBackupRepository scheduledBackupRepository;
    @Mock private ServerRepository serverRepository;
    @Mock private StorageTargetRepository storageTargetRepository;
    @Mock private UserRepository userRepository;
    @Mock private SnapshotEngine snapshotEngine;

    @InjectMocks
    private ScheduledBackupService scheduledBackupService;

    private User testUser;
    private Server testServer;
    private ScheduledBackup testBackup;

    @BeforeEach
    void setUp() {
        testUser = User.builder().id(1L).name("Test User").email("test@example.com").build();
        testServer = Server.builder().id(1L).name("Test Server").ip("192.168.1.1").status(Server.ServerStatus.RUNNING).build();
        testBackup = ScheduledBackup.builder().id(1L).user(testUser).server(testServer).name("Daily Backup")
                .cronExpression("0 2 * * *").enabled(true).runCount(5).build();
    }

    @Test
    void getAll_returnsUserBackups() {
        when(scheduledBackupRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(testBackup));
        var result = scheduledBackupService.getAll(1L);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Daily Backup", result.get(0).name());
    }

    @Test
    void getById_existingBackup_returnsBackup() {
        when(scheduledBackupRepository.findById(1L)).thenReturn(Optional.of(testBackup));
        var result = scheduledBackupService.getById(1L);
        assertNotNull(result);
        assertEquals("Daily Backup", result.name());
    }

    @Test
    void getById_nonExistingBackup_throwsException() {
        when(scheduledBackupRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> scheduledBackupService.getById(999L));
    }

    @Test
    void toggleEnabled_existingBackup_togglesState() {
        when(scheduledBackupRepository.findById(1L)).thenReturn(Optional.of(testBackup));
        testBackup.setEnabled(true);
        var result = scheduledBackupService.toggleEnabled(1L);
        assertFalse(result.enabled());
    }

    @Test
    void delete_existingBackup_deletes() {
        when(scheduledBackupRepository.existsById(1L)).thenReturn(true);
        scheduledBackupService.delete(1L);
        verify(scheduledBackupRepository).deleteById(1L);
    }

    @Test
    void delete_nonExistingBackup_throwsException() {
        when(scheduledBackupRepository.existsById(999L)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> scheduledBackupService.delete(999L));
    }
}
