package com.chronovault.service;

import com.chronovault.dto.branch.CreateBranchRequest;
import com.chronovault.entity.Server;
import com.chronovault.entity.ServerBranch;
import com.chronovault.exception.BadRequestException;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.ServerBranchRepository;
import com.chronovault.repository.ServerRepository;
import com.chronovault.repository.SnapshotRepository;
import com.chronovault.repository.StorageTargetRepository;
import com.chronovault.snapshot.ResticClient;
import com.chronovault.ssh.SshConnectionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServerBranchServiceTest {

    @Mock private ServerBranchRepository branchRepository;
    @Mock private ServerRepository serverRepository;
    @Mock private SnapshotRepository snapshotRepository;
    @Mock private StorageTargetRepository storageTargetRepository;
    @Mock private SshConnectionManager sshManager;
    @Mock private ResticClient resticClient;

    @InjectMocks
    private ServerBranchService service;

    private Server testServer;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "resticPassword", "test-password");
        testServer = Server.builder().id(1L).name("test-server").ip("10.0.0.1").build();
    }

    @Test
    void getBranches_serverNotFound_throwsException() {
        when(serverRepository.findById(999L)).thenReturn(java.util.Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.getBranches(999L));
    }

    @Test
    void getBranches_withBranches_returnsList() {
        when(serverRepository.findById(1L)).thenReturn(Optional.of(testServer));
        ServerBranch branch = ServerBranch.builder().id(1L).name("main").server(testServer).build();
        when(branchRepository.findByServerIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(branch));

        var result = service.getBranches(1L);
        assertEquals(1, result.size());
    }

    @Test
    void createBranch_serverNotFound_throwsException() {
        when(serverRepository.findById(999L)).thenReturn(java.util.Optional.empty());
        CreateBranchRequest request = new CreateBranchRequest("main", "desc", 1L);
        assertThrows(ResourceNotFoundException.class, () -> service.createBranch(999L, request, 1L));
    }

    @Test
    void createBranch_snapshotNotFound_throwsException() {
        when(serverRepository.findById(1L)).thenReturn(Optional.of(testServer));
        when(snapshotRepository.findById(1L)).thenReturn(java.util.Optional.empty());
        CreateBranchRequest request = new CreateBranchRequest("main", "desc", 1L);
        assertThrows(ResourceNotFoundException.class, () -> service.createBranch(1L, request, 1L));
    }
}