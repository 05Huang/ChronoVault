package com.chronovault.controller;

import com.chronovault.dto.risk.RiskDTO;
import com.chronovault.dto.risk.RiskNodeDTO;
import com.chronovault.dto.risk.RiskScoreDTO;
import com.chronovault.dto.risk.RiskTrendDTO;
import com.chronovault.service.RiskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RiskControllerTest {

    @Mock private RiskService riskService;

    @InjectMocks
    private RiskController controller;

    @Test
    void getScore_returnsScore() {
        RiskScoreDTO score = new RiskScoreDTO(85.0, "低风险", "Good", 0, 1, 0);
        when(riskService.getScore()).thenReturn(score);
        var response = controller.getScore();
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
    }

    @Test
    void getTrend_returnsList() {
        when(riskService.getTrend()).thenReturn(List.of());
        var response = controller.getTrend();
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void getNodes_returnsList() {
        RiskNodeDTO node = new RiskNodeDTO(1L, "Server 1", 85.0, "LOW");
        when(riskService.getNodes()).thenReturn(List.of(node));
        var response = controller.getNodes();
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void getRisks_returnsList() {
        when(riskService.getRisks()).thenReturn(List.of());
        var response = controller.getRisks();
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void triggerScan_succeeds() {
        when(riskService.scan()).thenReturn(List.of());
        var response = controller.scan();
        assertEquals(200, response.getStatusCode().value());
        verify(riskService).scan();
    }

    @Test
    void mitigate_succeeds() {
        doNothing().when(riskService).mitigate(1L);
        var response = controller.mitigate(1L);
        assertEquals(200, response.getStatusCode().value());
        verify(riskService).mitigate(1L);
    }
}