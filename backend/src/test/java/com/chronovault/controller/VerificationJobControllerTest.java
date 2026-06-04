package com.chronovault.controller;

import com.chronovault.entity.VerificationJob;
import com.chronovault.service.VerificationJobService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VerificationJobControllerTest {

    @Mock private VerificationJobService jobService;

    @InjectMocks
    private VerificationJobController controller;

    @Test
    void getJobs_returnsList() {
        when(jobService.getJobs()).thenReturn(List.of());
        var response = controller.getJobs();
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void createJob_succeeds() {
        VerificationJob job = VerificationJob.builder().id(1L).enabled(true).build();
        when(jobService.createJob(any(VerificationJob.class))).thenReturn(job);
        var response = controller.createJob(new VerificationJob());
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void updateJob_succeeds() {
        VerificationJob job = VerificationJob.builder().id(1L).enabled(false).build();
        when(jobService.updateJob(eq(1L), any(VerificationJob.class))).thenReturn(job);
        var response = controller.updateJob(1L, new VerificationJob());
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void deleteJob_succeeds() {
        doNothing().when(jobService).deleteJob(1L);
        var response = controller.deleteJob(1L);
        assertEquals(200, response.getStatusCode().value());
        verify(jobService).deleteJob(1L);
    }

    @Test
    void runJob_succeeds() {
        VerificationJob job = VerificationJob.builder().id(1L).lastStatus("SUCCESS").build();
        when(jobService.runJob(1L)).thenReturn(job);
        var response = controller.runJob(1L);
        assertEquals(200, response.getStatusCode().value());
    }
}