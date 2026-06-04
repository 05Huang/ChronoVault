package com.chronovault.controller;

import com.chronovault.dto.blame.ChangeAttribution;
import com.chronovault.service.ChangeAttributionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChangeAttributionControllerTest {

    @Mock private ChangeAttributionService attributionService;

    @InjectMocks
    private ChangeAttributionController controller;

    @Test
    void getServerBlame_returnsList() {
        when(attributionService.getServerBlame(1L)).thenReturn(List.of());
        var response = controller.getServerBlame(1L);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void getSnapshotBlame_returnsList() {
        when(attributionService.getSnapshotBlame(1L)).thenReturn(List.of());
        var response = controller.getSnapshotBlame(1L);
        assertEquals(200, response.getStatusCode().value());
    }
}