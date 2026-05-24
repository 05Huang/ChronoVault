package com.chronovault.docker;

import com.chronovault.entity.Container;
import com.chronovault.entity.Server;
import com.chronovault.entity.Volume;
import com.chronovault.ssh.SshConnection;
import com.chronovault.ssh.SshConnectionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DockerOperationServiceTest {

    @Mock
    private SshConnectionManager sshManager;

    @Mock
    private SshConnection sshConnection;

    private DockerOperationService dockerService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Server testServer;

    @BeforeEach
    void setUp() throws Exception {
        dockerService = new DockerOperationService(sshManager, objectMapper);
        testServer = new Server();
        testServer.setId(1L);
        testServer.setIp("192.168.1.100");
        testServer.setSshPort(22);
        testServer.setSshUsername("root");
        lenient().when(sshManager.getConnection(testServer)).thenReturn(sshConnection);
    }

    @Test
    void listContainers_parsesDockerPsOutput() throws Exception {
        String dockerOutput = """
                ===CONTAINERS===
                {"Names":"nginx","Image":"nginx:latest","State":"Running","Status":"Up 2 hours","Ports":"80/tcp"}
                {"Names":"redis","Image":"redis:7","State":"Running","Status":"Up 5 hours","Ports":"6379/tcp"}
                {"Names":"postgres","Image":"postgres:15","State":"Exited","Status":"Exited (0) 10 minutes ago","Ports":""}
                ===STATS===
                {"Name":"nginx","CPUPerc":"2.50%","MemPerc":"10.50%","MemUsage":"210MiB / 2GiB","BlockIO":"100MB / 50MB"}
                {"Name":"redis","CPUPerc":"0.50%","MemPerc":"3.20%","MemUsage":"64MiB / 2GiB","BlockIO":"10MB / 5MB"}
                """;
        when(sshConnection.executeCommand(anyString()))
                .thenReturn(new SshConnection.CommandResult(0, dockerOutput, ""));

        List<Container> containers = dockerService.listContainers(testServer);

        assertEquals(3, containers.size());
        assertEquals("nginx", containers.get(0).getName());
        assertEquals(Container.ContainerType.NGINX, containers.get(0).getType());
        assertEquals(Container.ContainerStatus.RUNNING, containers.get(0).getStatus());
        assertEquals(2.5, containers.get(0).getCpuPercent());
        assertEquals(10.5, containers.get(0).getMemoryPercent());
        assertEquals(210L, containers.get(0).getMemoryMb());
        assertEquals("redis", containers.get(1).getName());
        assertEquals(Container.ContainerType.REDIS, containers.get(1).getType());
        assertEquals("postgres", containers.get(2).getName());
        assertEquals(Container.ContainerType.MYSQL, containers.get(2).getType());
        assertEquals(Container.ContainerStatus.STOPPED, containers.get(2).getStatus());
    }

    @Test
    void listContainers_dockerFails_returnsEmpty() throws Exception {
        when(sshConnection.executeCommand(anyString()))
                .thenReturn(new SshConnection.CommandResult(1, "", "permission denied"));

        List<Container> containers = dockerService.listContainers(testServer);
        assertTrue(containers.isEmpty());
    }

    @Test
    void listVolumes_parsesDockerVolumeOutput() throws Exception {
        String dockerOutput = """
                {"Name":"data-volume","Mountpoint":"/var/lib/docker/volumes/data-volume/_data"}
                {"Name":"config-volume","Mountpoint":"/var/lib/docker/volumes/config-volume/_data"}
                """;
        when(sshConnection.executeCommand("docker volume ls --format '{{json .}}'"))
                .thenReturn(new SshConnection.CommandResult(0, dockerOutput, ""));

        List<Volume> volumes = dockerService.listVolumes(testServer);

        assertEquals(2, volumes.size());
        assertEquals("data-volume", volumes.get(0).getName());
        assertEquals("ACTIVE", volumes.get(0).getStatus());
    }

    @Test
    void startContainer_validId_callsDockerStart() throws Exception {
        when(sshConnection.executeCommand("docker start abc123"))
                .thenReturn(new SshConnection.CommandResult(0, "abc123", ""));

        boolean result = dockerService.startContainer(testServer, "abc123");
        assertTrue(result);
        verify(sshConnection).executeCommand("docker start abc123");
    }

    @Test
    void stopContainer_validId_callsDockerStop() throws Exception {
        when(sshConnection.executeCommand("docker stop abc123"))
                .thenReturn(new SshConnection.CommandResult(0, "abc123", ""));

        boolean result = dockerService.stopContainer(testServer, "abc123");
        assertTrue(result);
    }

    @Test
    void removeContainer_withForce_callsDockerRmF() throws Exception {
        when(sshConnection.executeCommand("docker rm -f abc123"))
                .thenReturn(new SshConnection.CommandResult(0, "abc123", ""));

        boolean result = dockerService.removeContainer(testServer, "abc123", true);
        assertTrue(result);
        verify(sshConnection).executeCommand("docker rm -f abc123");
    }

    @Test
    void removeContainer_withoutForce_callsDockerRm() throws Exception {
        when(sshConnection.executeCommand("docker rm abc123"))
                .thenReturn(new SshConnection.CommandResult(0, "abc123", ""));

        boolean result = dockerService.removeContainer(testServer, "abc123", false);
        assertTrue(result);
        verify(sshConnection).executeCommand("docker rm abc123");
    }

    @Test
    void createContainer_validImage_returnsContainerId() throws Exception {
        when(sshConnection.executeCommand("docker run -d --name myapp nginx:latest"))
                .thenReturn(new SshConnection.CommandResult(0, "sha256:abc123def456\n", ""));

        String id = dockerService.createContainer(testServer, "nginx:latest", "myapp", null, null, null);
        assertNotNull(id);
        assertTrue(id.contains("abc123def456"));
    }

    @Test
    void createContainer_withPortMapping_includesFlag() throws Exception {
        when(sshConnection.executeCommand("docker run -d -p 8080:80 -p 443:443 nginx:latest"))
                .thenReturn(new SshConnection.CommandResult(0, "container123\n", ""));

        String id = dockerService.createContainer(testServer, "nginx:latest", null, "8080:80,443:443", null, null);
        assertNotNull(id);
    }

    @Test
    void createContainer_invalidImage_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> dockerService.createContainer(testServer, "invalid image name!", null, null, null, null));
    }

    @Test
    void createContainer_invalidPortFormat_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> dockerService.createContainer(testServer, "nginx", null, "not-a-port:80", null, null));
    }

    @Test
    void createContainer_invalidEnvFormat_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> dockerService.createContainer(testServer, "nginx", null, null, null, "=invalid"));
    }

    @Test
    void startContainer_invalidId_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> dockerService.startContainer(testServer, "invalid; rm -rf /"));
    }

    @Test
    void listImages_parsesDockerImagesOutput() throws Exception {
        String dockerOutput = """
                {"Repository":"nginx","Tag":"latest","ID":"sha256:abc123","Size":"142MB","CreatedAt":"2024-01-01"}
                {"Repository":"redis","Tag":"7","ID":"sha256:def456","Size":"117MB","CreatedAt":"2024-01-02"}
                """;
        when(sshConnection.executeCommand("docker images --format '{{json .}}'"))
                .thenReturn(new SshConnection.CommandResult(0, dockerOutput, ""));

        List<Map<String, String>> images = dockerService.listImages(testServer);

        assertEquals(2, images.size());
        assertEquals("nginx", images.get(0).get("repository"));
        assertEquals("latest", images.get(0).get("tag"));
        assertEquals("142MB", images.get(0).get("size"));
    }

    @Test
    void pullImage_validImage_callsDockerPull() throws Exception {
        when(sshConnection.executeCommand(eq("docker pull nginx:latest"), any()))
                .thenReturn(new SshConnection.CommandResult(0, "Pull complete", ""));

        boolean result = dockerService.pullImage(testServer, "nginx:latest");
        assertTrue(result);
    }

    @Test
    void pullImage_invalidName_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> dockerService.pullImage(testServer, "invalid image!"));
    }

    @Test
    void listNetworks_parsesDockerNetworkOutput() throws Exception {
        String dockerOutput = """
                {"ID":"abc123","Name":"bridge","Driver":"bridge","Scope":"local"}
                {"ID":"def456","Name":"host","Driver":"host","Scope":"local"}
                """;
        when(sshConnection.executeCommand("docker network ls --format '{{json .}}'"))
                .thenReturn(new SshConnection.CommandResult(0, dockerOutput, ""));

        List<Map<String, String>> networks = dockerService.listNetworks(testServer);

        assertEquals(2, networks.size());
        assertEquals("bridge", networks.get(0).get("name"));
        assertEquals("bridge", networks.get(0).get("driver"));
    }

    @Test
    void getContainerLogs_returnsLogOutput() throws Exception {
        when(sshConnection.executeCommand("docker logs --tail 100 nginx 2>&1"))
                .thenReturn(new SshConnection.CommandResult(0, "2024-01-01 GET / 200\n2024-01-01 POST /api 201\n", ""));

        String logs = dockerService.getContainerLogs(testServer, "nginx", 100);
        assertTrue(logs.contains("GET /"));
    }

    @Test
    void restartContainer_callsDockerRestart() throws Exception {
        when(sshConnection.executeCommand("docker restart nginx"))
                .thenReturn(new SshConnection.CommandResult(0, "nginx", ""));

        boolean result = dockerService.restartContainer(testServer, "nginx");
        assertTrue(result);
    }

    @Test
    void getServerSystemInfo_callsUnameAndDfAndFree() throws Exception {
        when(sshConnection.executeCommand("uname -a && df -h / && free -h && uptime"))
                .thenReturn(new SshConnection.CommandResult(0, "Linux server1 5.15.0 x86_64\n", ""));

        String info = dockerService.getServerSystemInfo(testServer);
        assertTrue(info.contains("Linux"));
    }
}
