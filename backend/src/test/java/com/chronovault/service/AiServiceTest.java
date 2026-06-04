package com.chronovault.service;

import com.chronovault.ai.AiAnalysisService;
import com.chronovault.ai.AiClient;
import com.chronovault.entity.AiInsight;
import com.chronovault.entity.AiRecommendation;
import com.chronovault.repository.*;
import com.chronovault.ssh.SshConnectionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiServiceTest {

    @Mock private AiInsightRepository aiInsightRepository;
    @Mock private AiRecommendationRepository aiRecommendationRepository;
    @Mock private AiAnalysisService aiAnalysisService;
    @Mock private ServerRepository serverRepository;
    @Mock private ContainerRepository containerRepository;
    @Mock private VolumeRepository volumeRepository;
    @Mock private SnapshotRepository snapshotRepository;
    @Mock private StorageTargetRepository storageTargetRepository;
    @Mock private AiClient aiClient;
    @Mock private SshConnectionManager sshManager;

    @InjectMocks
    private AiService service;

    @Test
    void getInsights_empty_seedsDefaults() {
        when(aiInsightRepository.findTop50ByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(aiAnalysisService.generateReport()).thenReturn("Test report");
        when(serverRepository.count()).thenReturn(5L);
        when(serverRepository.countByStatus(any())).thenReturn(3L);

        var result = service.getInsights();
        assertNotNull(result);
        // Should have seeded at least 1 insight
        verify(aiInsightRepository, atLeastOnce()).save(any(AiInsight.class));
    }

    @Test
    void getInsights_withData_returnsList() {
        AiInsight insight = AiInsight.builder().id(1L).title("Test Insight").description("desc")
                .category("SYSTEM").severity("INFO").build();
        when(aiInsightRepository.findTop50ByOrderByCreatedAtDesc()).thenReturn(List.of(insight));

        var result = service.getInsights();
        assertEquals(1, result.size());
        assertEquals("Test Insight", result.get(0).title());
    }

    @Test
    void getRecommendations_empty_seedsDefaults() {
        when(aiRecommendationRepository.findTop50ByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(snapshotRepository.count()).thenReturn(0L);
        when(storageTargetRepository.sumUsedBytes()).thenReturn(0L);
        when(storageTargetRepository.sumTotalBytes()).thenReturn(1000L);
        when(serverRepository.count()).thenReturn(0L);
        when(containerRepository.count()).thenReturn(0L);

        var result = service.getRecommendations();
        assertNotNull(result);
        verify(aiRecommendationRepository, atLeastOnce()).saveAll(anyList());
    }

    @Test
    void getRecommendations_withData_returnsList() {
        AiRecommendation rec = AiRecommendation.builder().id(1L).title("Test Rec").description("desc")
                .impact("HIGH").applied(false).build();
        when(aiRecommendationRepository.findTop50ByOrderByCreatedAtDesc()).thenReturn(List.of(rec));

        var result = service.getRecommendations();
        assertEquals(1, result.size());
    }
}