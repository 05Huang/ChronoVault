package com.chronovault.controller;

import com.chronovault.dto.ai.*;
import com.chronovault.service.AiService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiControllerTest {

    @Mock private AiService aiService;

    @InjectMocks
    private AiController controller;

    @Test
    void getInsights_returnsList() {
        AiInsightDTO insight = new AiInsightDTO(1L, "Test Insight", "desc", "SYSTEM", "INFO");
        when(aiService.getInsights()).thenReturn(List.of(insight));
        var response = controller.getInsights();
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
    }

    @Test
    void getRecommendations_returnsList() {
        AiRecommendationDTO rec = new AiRecommendationDTO(1L, "Test Rec", "desc", "check_circle", "HIGH", false);
        when(aiService.getRecommendations()).thenReturn(List.of(rec));
        var response = controller.getRecommendations();
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void applyRecommendation_succeeds() {
        doNothing().when(aiService).applyRecommendation(1L);
        var response = controller.applyRecommendation(1L);
        assertEquals(200, response.getStatusCode().value());
        verify(aiService).applyRecommendation(1L);
    }

    @Test
    void getRiskRadar_returnsData() {
        RiskRadarDTO radar = new RiskRadarDTO(List.of(), List.of());
        when(aiService.getRiskRadar()).thenReturn(radar);
        var response = controller.getRiskRadar();
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void getServerAnalysis_returnsData() {
        ServerAnalysisDTO analysis = ServerAnalysisDTO.builder()
                .healthScore(85).summary("Good").findings(List.of()).recommendations(List.of()).build();
        when(aiService.analyzeServer(1L)).thenReturn(analysis);
        var response = controller.analyzeServer(1L);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void getStoragePrediction_returnsData() {
        when(aiService.getStoragePrediction()).thenReturn(new java.util.HashMap<>());
        var response = controller.getStoragePrediction();
        assertEquals(200, response.getStatusCode().value());
    }
}