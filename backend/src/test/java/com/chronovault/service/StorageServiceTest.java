package com.chronovault.service;

import com.chronovault.dto.storage.StorageOverviewDTO;
import com.chronovault.dto.storage.StorageDistributionDTO;
import com.chronovault.dto.storage.StorageHealthDTO;
import com.chronovault.entity.StorageTarget;
import com.chronovault.entity.User;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.StorageTargetRepository;
import com.chronovault.security.CredentialEncryptor;
import com.chronovault.storage.StorageHealthChecker;
import com.chronovault.storage.StorageProvider;
import com.chronovault.storage.StorageRouter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock private StorageTargetRepository storageTargetRepository;
    @Mock private StorageHealthChecker healthChecker;
    @Mock private StorageRouter storageRouter;
    @Mock private UserService userService;
    @Mock private CredentialEncryptor encryptor;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private StorageService storageService;

    private StorageTarget testTarget;
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder().id(1L).name("Test User").email("test@example.com").build();
        testTarget = StorageTarget.builder()
                .id(1L).user(testUser).type(StorageTarget.StorageType.LOCAL)
                .name("Test Storage").endpoint("/backup")
                .usedBytes(500L).totalBytes(1000L)
                .status(StorageTarget.StorageStatus.ACTIVE).build();
    }

    @Test
    void getOverview_returnsStorageList() {
        when(storageTargetRepository.findAll()).thenReturn(List.of(testTarget));

        List<StorageOverviewDTO> result = storageService.getOverview();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Test Storage", result.get(0).name());
        assertEquals(50.0, result.get(0).usagePercent(), 0.1);
    }

    @Test
    void getOverview_emptyList() {
        when(storageTargetRepository.findAll()).thenReturn(List.of());

        List<StorageOverviewDTO> result = storageService.getOverview();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getDistribution_returnsDistribution() {
        when(storageTargetRepository.sumUsedBytes()).thenReturn(1000L);
        when(storageTargetRepository.findAll()).thenReturn(List.of(testTarget));

        List<StorageDistributionDTO> result = storageService.getDistribution();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Test Storage", result.get(0).name());
    }

    @Test
    void getHealth_emptyTargets_returnsDefault() {
        when(storageTargetRepository.findAll()).thenReturn(List.of());

        StorageHealthDTO result = storageService.getHealth();

        assertNotNull(result);
        assertEquals("暂无存储目标", result.status());
    }

    @Test
    void getHealth_healthyTargets_returnsHealthy() {
        when(storageTargetRepository.findAll()).thenReturn(List.of(testTarget));
        StorageProvider.StorageHealthInfo healthyInfo = new StorageProvider.StorageHealthInfo("HEALTHY", "100", "5ms", "10MB/s", 0);
        when(healthChecker.getHealth(1L)).thenReturn(healthyInfo);

        StorageHealthDTO result = storageService.getHealth();

        assertEquals("健康", result.status());
    }

    @Test
    void deleteTarget_existingTarget_deletes() {
        when(storageTargetRepository.findById(1L)).thenReturn(Optional.of(testTarget));

        storageService.deleteTarget(1L);

        verify(storageTargetRepository).delete(testTarget);
    }

    @Test
    void deleteTarget_nonExistingTarget_throwsException() {
        when(storageTargetRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> storageService.deleteTarget(999L));
    }

    @Test
    void addTarget_validRequest_savesTarget() throws Exception {
        when(userService.getByEmail("test@example.com")).thenReturn(testUser);
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        lenient().when(encryptor.encrypt(anyString())).thenReturn("encrypted");
        lenient().when(storageRouter.getProvider(any())).thenReturn(mock(StorageProvider.class));

        StorageOverviewDTO result = storageService.addTarget(
                "test@example.com", "LOCAL", "New Storage", "/backup2", 2000L,
                null, null, null, null);

        assertNotNull(result);
        verify(storageTargetRepository).save(any(StorageTarget.class));
    }
}
