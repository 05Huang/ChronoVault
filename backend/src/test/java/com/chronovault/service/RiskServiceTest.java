package com.chronovault.service;

import com.chronovault.dto.risk.RiskDTO;
import com.chronovault.dto.risk.RiskNodeDTO;
import com.chronovault.dto.risk.RiskScoreDTO;
import com.chronovault.entity.Risk;
import com.chronovault.entity.Server;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.RiskRepository;
import com.chronovault.repository.ServerRepository;
import com.chronovault.ssh.SshConnection;
import com.chronovault.ssh.SshConnectionManager;
import com.chronovault.task.AsyncTaskManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RiskServiceTest {

    @Mock private RiskRepository riskRepository;
    @Mock private ServerRepository serverRepository;
    @Mock private SshConnectionManager sshManager;
    @Mock private AsyncTaskManager taskManager;
    @Mock private SshConnection sshConnection;

    @InjectMocks
    private RiskService riskService;

    private Server testServer;

    @BeforeEach
    void setUp() {
        testServer = Server.builder().id(1L).name("Test Server").ip("192.168.1.1").status(Server.ServerStatus.RUNNING).build();
    }

    @Test
    void getScore_noRisks_returnsHighScore() {
        when(riskRepository.countByLevel(Risk.RiskLevel.CRITICAL)).thenReturn(0L);
        when(riskRepository.countByLevel(Risk.RiskLevel.WARNING)).thenReturn(0L);
        when(riskRepository.countByLevel(Risk.RiskLevel.ANOMALOUS)).thenReturn(0L);

        RiskScoreDTO result = riskService.getScore();

        assertNotNull(result);
        assertEquals(100.0, result.overallScore(), 0.1);
        assertEquals("低风险", result.level());
    }

    @Test
    void getScore_criticalRisks_returnsLowScore() {
        when(riskRepository.countByLevel(Risk.RiskLevel.CRITICAL)).thenReturn(5L);
        when(riskRepository.countByLevel(Risk.RiskLevel.WARNING)).thenReturn(0L);
        when(riskRepository.countByLevel(Risk.RiskLevel.ANOMALOUS)).thenReturn(0L);

        RiskScoreDTO result = riskService.getScore();

        assertNotNull(result);
        assertEquals(0.0, result.overallScore(), 0.1);
        assertEquals("极高风险", result.level());
    }

    @Test
    void getNodes_withServer_returnsNode() throws Exception {
        when(serverRepository.findAll()).thenReturn(List.of(testServer));
        when(sshManager.getConnection(testServer)).thenReturn(sshConnection);
        when(sshConnection.executeCommand(contains("df"))).thenReturn(new SshConnection.CommandResult(0, "50", ""));

        List<RiskNodeDTO> result = riskService.getNodes();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Test Server", result.get(0).name());
    }

    @Test
    void getNodes_emptyServers_returnsDefaultNode() {
        when(serverRepository.findAll()).thenReturn(List.of());

        List<RiskNodeDTO> result = riskService.getNodes();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("无在线服务器", result.get(0).name());
    }

    @Test
    void mitigate_existingRisk_setsMitigated() {
        Risk risk = Risk.builder().id(1L).status(Risk.RiskStatus.OPEN).build();
        when(riskRepository.findById(1L)).thenReturn(Optional.of(risk));

        riskService.mitigate(1L);

        assertEquals(Risk.RiskStatus.MITIGATED, risk.getStatus());
        verify(riskRepository).save(risk);
    }

    @Test
    void mitigate_nonExistingRisk_throwsException() {
        when(riskRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> riskService.mitigate(999L));
    }
}
