package com.chronovault.service;

import com.chronovault.entity.VerificationJob;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.SnapshotRepository;
import com.chronovault.repository.StorageTargetRepository;
import com.chronovault.repository.VerificationJobRepository;
import com.chronovault.snapshot.ResticClient;
import com.chronovault.ssh.SshConnectionManager;
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
class VerificationJobServiceTest {

    @Mock private VerificationJobRepository jobRepository;
    @Mock private SnapshotRepository snapshotRepository;
    @Mock private StorageTargetRepository storageTargetRepository;
    @Mock private SshConnectionManager sshManager;
    @Mock private ResticClient resticClient;

    @InjectMocks
    private VerificationJobService service;

    @Test
    void getJobs_returnsAllJobs() {
        VerificationJob job = VerificationJob.builder().id(1L).enabled(true).build();
        when(jobRepository.findAll()).thenReturn(List.of(job));
        var result = service.getJobs();
        assertEquals(1, result.size());
    }

    @Test
    void createJob_savesJob() {
        VerificationJob job = VerificationJob.builder().enabled(true).build();
        when(jobRepository.save(any(VerificationJob.class))).thenAnswer(inv -> {
            VerificationJob j = inv.getArgument(0);
            var field = VerificationJob.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(j, 1L);
            return j;
        });
        var result = service.createJob(job);
        assertNotNull(result);
        verify(jobRepository).save(any(VerificationJob.class));
    }

    @Test
    void updateJob_nonExisting_throwsException() {
        when(jobRepository.findById(999L)).thenReturn(Optional.empty());
        VerificationJob updates = VerificationJob.builder().enabled(false).build();
        assertThrows(ResourceNotFoundException.class, () -> service.updateJob(999L, updates));
    }

    @Test
    void deleteJob_existing_deletes() {
        service.deleteJob(1L);
        verify(jobRepository).deleteById(1L);
    }
}