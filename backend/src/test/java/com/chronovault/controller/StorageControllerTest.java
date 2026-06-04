package com.chronovault.controller;

import com.chronovault.dto.storage.*;
import com.chronovault.service.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorageControllerTest {

    @Mock
    private StorageService storageService;

    @InjectMocks
    private StorageController controller;

    private Authentication auth() {
        return new UsernamePasswordAuthenticationToken("test@test.com", null);
    }

    @Test
    void getOverview_returnsList() {
        when(storageService.getOverview()).thenReturn(List.of(
                new StorageOverviewDTO(1L, "LOCAL", "Local Storage", 1024L, 10240L, 10.0, "ACTIVE")));

        var response = controller.getOverview();
        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().data().size());
        assertEquals("LOCAL", response.getBody().data().get(0).type());
    }

    @Test
    void getDistribution_returnsList() {
        when(storageService.getDistribution()).thenReturn(List.of());

        var response = controller.getDistribution();
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void getHealth_returnsHealth() {
        when(storageService.getHealth()).thenReturn(new StorageHealthDTO("healthy", "100", "2ms", "50MB/s", 0));

        var response = controller.getHealth();
        assertEquals(200, response.getStatusCode().value());
        assertEquals("healthy", response.getBody().data().status());
    }

    @Test
    void addTarget_validRequest_succeeds() {
        when(storageService.addTarget("test@test.com", "LOCAL", "My Storage", null, null, null, null, null, null))
                .thenReturn(new StorageOverviewDTO(1L, "LOCAL", "My Storage", 0L, 0L, 0.0, "ACTIVE"));

        CreateStorageRequest request = new CreateStorageRequest("LOCAL", "My Storage", null, null, null, null, null, null);
        var response = controller.addTarget(auth(), request);
        assertEquals(201, response.getStatusCode().value());
        assertEquals("My Storage", response.getBody().data().name());
    }

    @Test
    void deleteTarget_validId_succeeds() {
        doNothing().when(storageService).deleteTarget(1L);

        var response = controller.deleteTarget(1L);
        assertEquals(200, response.getStatusCode().value());
        verify(storageService).deleteTarget(1L);
    }
}