package com.chronovault.controller;

import com.chronovault.dto.drift.DriftReportDTO;
import com.chronovault.service.DriftDetectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriftDetectionControllerTest {

    @Mock private DriftDetectionService driftService;

    @InjectMocks
    private DriftDetectionController controller;

    @Test
    void detectDrift_returnsReport() {
        DriftReportDTO report = new DriftReportDTO(1L, "server1", 0, List.of(), List.of(), List.of(), "NO_DRIFT", "2026-01-01T00:00:00");
        when(driftService.detectDrift(1L)).thenReturn(report);
        var response = controller.detectDrift(1L);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
    }
}