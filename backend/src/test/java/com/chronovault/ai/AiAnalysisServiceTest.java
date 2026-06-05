package com.chronovault.ai;

import com.chronovault.cache.CacheService;
import com.chronovault.entity.*;
import com.chronovault.repository.*;
import com.chronovault.ssh.SshConnectionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiAnalysisServiceTest {

    @Mock private AiClient aiClient;
    @Mock private CacheService cacheService;
    @Mock private ObjectMapper objectMapper;
    @Mock private ServerRepository serverRepository;
    @Mock private SnapshotRepository snapshotRepository;
    @Mock private StorageTargetRepository storageTargetRepository;
    @Mock private RiskRepository riskRepository;
    @Mock private AlertRepository alertRepository;
    @Mock private SshConnectionManager sshManager;

    @InjectMocks
    private AiAnalysisService service;

    @Test
    void getRiskRadar_noCachedData_computesScores() {
        when(cacheService.get(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class))).thenReturn(null);
        when(riskRepository.countByLevel(Risk.RiskLevel.CRITICAL)).thenReturn(0L);
        when(serverRepository.findAll()).thenReturn(List.of(
                Server.builder().id(1L).status(Server.ServerStatus.RUNNING).build()
        ));
        when(snapshotRepository.count()).thenReturn(10L);
        when(snapshotRepository.countByStatus(Snapshot.SnapshotStatus.STABLE)).thenReturn(8L);
        when(snapshotRepository.countToday()).thenReturn(2L);
        when(alertRepository.countToday()).thenReturn(0L);
        when(storageTargetRepository.sumUsedBytes()).thenReturn(500L * 1024L * 1024L);
        when(storageTargetRepository.sumTotalBytes()).thenReturn(1000L * 1024L * 1024L);

        var result = service.getRiskRadar();

        assertNotNull(result);
        assertEquals(5, result.size());
        assertTrue(result.containsKey("数据安全"));
        assertTrue(result.containsKey("系统稳定"));
        assertTrue(result.containsKey("备份完整"));
        assertTrue(result.containsKey("网络防护"));
        assertTrue(result.containsKey("存储健康"));
    }

    @Test
    void getRiskRadar_withCachedData_returnsCached() {
        Map<String, Double> cached = Map.of("数据安全", 90.0, "系统稳定", 85.0,
                "备份完整", 80.0, "网络防护", 95.0, "存储健康", 70.0);
        when(cacheService.get(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class))).thenReturn(cached);

        var result = service.getRiskRadar();

        assertEquals(cached, result);
        verify(riskRepository, never()).countByLevel(any());
    }

    @Test
    void getStoragePrediction_noHistory_buildsPrediction() {
        when(cacheService.get(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class))).thenReturn(null);
        when(snapshotRepository.findAll()).thenReturn(List.of());
        when(storageTargetRepository.sumUsedBytes()).thenReturn(100L * 1024L * 1024L);

        var result = service.getStoragePrediction();

        assertNotNull(result);
        assertTrue(result.containsKey("months"));
        assertTrue(result.containsKey("actual"));
        assertTrue(result.containsKey("predicted"));
    }

    @Test
    void getStoragePrediction_withHistory_usesActualData() {
        when(cacheService.get(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class))).thenReturn(null);

        Snapshot s1 = Snapshot.builder().id(1L).sizeBytes(100L * 1024L * 1024L)
                .createdAt(LocalDateTime.now().minusDays(30)).build();
        Snapshot s2 = Snapshot.builder().id(2L).sizeBytes(200L * 1024L * 1024L)
                .createdAt(LocalDateTime.now()).build();
        when(snapshotRepository.findAll()).thenReturn(List.of(s1, s2));

        var result = service.getStoragePrediction();

        assertNotNull(result);
        assertTrue(result.containsKey("months"));
        assertTrue(result.containsKey("predicted"));
    }

    @Test
    void generateReport_aiEnabled_usesAI() {
        when(serverRepository.findAll()).thenReturn(List.of());
        when(snapshotRepository.count()).thenReturn(0L);
        when(storageTargetRepository.sumUsedBytes()).thenReturn(0L);
        when(storageTargetRepository.sumTotalBytes()).thenReturn(0L);
        when(riskRepository.countByLevel(Risk.RiskLevel.CRITICAL)).thenReturn(0L);
        when(riskRepository.countByLevel(Risk.RiskLevel.WARNING)).thenReturn(0L);
        when(alertRepository.countToday()).thenReturn(0L);
        when(aiClient.chat(anyString(), anyString())).thenReturn("AI Report Content");

        var result = service.generateReport();

        assertEquals("AI Report Content", result);
        verify(aiClient).chat(anyString(), anyString());
    }

    @Test
    void generateReport_aiDisabled_usesFallback() {
        when(serverRepository.findAll()).thenReturn(List.of());
        when(snapshotRepository.count()).thenReturn(0L);
        when(storageTargetRepository.sumUsedBytes()).thenReturn(0L);
        when(storageTargetRepository.sumTotalBytes()).thenReturn(0L);
        when(riskRepository.countByLevel(Risk.RiskLevel.CRITICAL)).thenReturn(0L);
        when(riskRepository.countByLevel(Risk.RiskLevel.WARNING)).thenReturn(0L);
        when(alertRepository.countToday()).thenReturn(0L);
        when(aiClient.chat(anyString(), anyString())).thenReturn(null);

        var result = service.generateReport();

        assertNotNull(result);
        assertTrue(result.contains("ChronoVault"));
        assertTrue(result.contains("基础分析"));
    }

    @Test
    void analyzeEnvironment_withScanData_usesAI() {
        Server server = Server.builder().id(1L).name("Test Server").build();
        when(serverRepository.findById(1L)).thenReturn(java.util.Optional.of(server));
        when(aiClient.chat(anyString(), anyString())).thenReturn("AI Analysis");

        Map<String, Object> scanData = Map.of(
                "os", "Ubuntu 22.04",
                "disk", "50GB used / 100GB total",
                "memory", "4GB / 8GB",
                "uptime", "3 days",
                "dockerInstalled", true,
                "containers", List.of(Map.of("name", "nginx", "image", "nginx:latest", "status", "running"))
        );

        var result = service.analyzeEnvironment(1L, scanData);

        assertEquals("AI Analysis", result);
        verify(aiClient).chat(anyString(), anyString());
    }

    @Test
    void analyzeEnvironment_aiDisabled_usesFallback() {
        when(serverRepository.findById(1L)).thenReturn(java.util.Optional.of(
                Server.builder().id(1L).name("Test Server").build()));
        when(aiClient.chat(anyString(), anyString())).thenReturn(null);

        Map<String, Object> scanData = Map.of(
                "os", "Ubuntu 22.04",
                "disk", "50GB",
                "memory", "4GB",
                "uptime", "3 days",
                "dockerInstalled", true,
                "containers", List.of(Map.of("name", "nginx", "image", "nginx:latest", "status", "running"))
        );

        var result = service.analyzeEnvironment(1L, scanData);

        assertNotNull(result);
        assertTrue(result.contains("环境分析"));
        assertTrue(result.contains("Docker"));
    }

    @Test
    void analyzeEnvironment_serverNotFound_usesFallback() {
        when(serverRepository.findById(999L)).thenReturn(java.util.Optional.empty());
        when(aiClient.chat(anyString(), anyString())).thenReturn(null);

        Map<String, Object> scanData = Map.of("os", "Ubuntu", "dockerInstalled", false);

        var result = service.analyzeEnvironment(999L, scanData);

        assertNotNull(result);
        assertTrue(result.contains("环境分析"));
    }
}