package com.chronovault.service;

import com.chronovault.entity.Server;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.ServerRepository;
import com.chronovault.ssh.SshConnection;
import com.chronovault.ssh.SshConnectionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private ServerRepository serverRepository;

    @Mock
    private SshConnectionManager sshManager;

    @Mock
    private SshConnection sshConnection;

    @InjectMocks
    private FileService fileService;

    private Server testServer;

    @BeforeEach
    void setUp() throws Exception {
        testServer = Server.builder()
                .id(1L)
                .ip("192.168.1.100")
                .sshPort(22)
                .sshUsername("root")
                .build();
        lenient().when(serverRepository.findById(1L)).thenReturn(Optional.of(testServer));
        lenient().when(sshManager.getConnection(any(Server.class))).thenReturn(sshConnection);
    }

    // --- Path Validation Tests ---

    @Test
    void browse_pathTraversal_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> fileService.browse(1L, "../../../etc"));
    }

    @Test
    void browse_tildePath_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> fileService.browse(1L, "~/.ssh"));
    }

    @Test
    void browse_emptyPath_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> fileService.browse(1L, ""));
    }

    @Test
    void browse_nullPath_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> fileService.browse(1L, null));
    }

    @Test
    void readFile_pathTraversal_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> fileService.readFile(1L, "../../../etc/passwd", 100));
    }

    @Test
    void downloadFile_pathTraversal_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> fileService.downloadFile(1L, "../secret"));
    }

    @Test
    void deleteFile_pathTraversal_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> fileService.deleteFile(1L, "../../important", false));
    }

    @Test
    void chmod_invalidMode_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> fileService.chmod(1L, "/tmp/file", "invalid"));
    }

    @Test
    void chmod_validOctalMode_doesNotThrow() throws Exception {
        when(sshConnection.executeCommand(anyString()))
                .thenReturn(new SshConnection.CommandResult(0, "", ""));
        assertDoesNotThrow(() -> fileService.chmod(1L, "/tmp/file", "755"));
    }

    @Test
    void chmod_validSymbolicMode_doesNotThrow() throws Exception {
        when(sshConnection.executeCommand(anyString()))
                .thenReturn(new SshConnection.CommandResult(0, "", ""));
        assertDoesNotThrow(() -> fileService.chmod(1L, "/tmp/file", "u+x"));
    }

    // --- Browse Tests ---

    @Test
    void browse_validPath_parsesLsOutput() throws Exception {
        String lsOutput = """
                total 20
                drwxr-xr-x 3 root root 4096 2024-01-15 10:30 .
                drwxr-xr-x 5 root root 4096 2024-01-10 08:00 ..
                -rw-r--r-- 1 root root  512 2024-01-15 10:30 config.yml
                lrwxrwxrwx 1 root root   11 2024-01-15 10:30 link -> config.yml
                """;
        when(sshConnection.executeCommand("ls -la --time-style=long-iso '/etc' 2>/dev/null"))
                .thenReturn(new SshConnection.CommandResult(0, lsOutput, ""));

        List<Map<String, String>> entries = fileService.browse(1L, "/etc");

        assertEquals(4, entries.size());
        assertEquals("drwxr-xr-x", entries.get(0).get("permissions"));
        assertEquals("root", entries.get(0).get("owner"));
        assertEquals("true", entries.get(0).get("isDir"));
        assertEquals("config.yml", entries.get(2).get("name"));
        assertEquals("false", entries.get(2).get("isDir"));
        assertEquals("true", entries.get(3).get("isLink"));
    }

    @Test
    void browse_sshFails_returnsEmpty() throws Exception {
        when(sshConnection.executeCommand(anyString()))
                .thenReturn(new SshConnection.CommandResult(1, "", "permission denied"));

        List<Map<String, String>> entries = fileService.browse(1L, "/root");
        assertTrue(entries.isEmpty());
    }

    @Test
    void browse_serverNotFound_throwsResourceNotFound() {
        when(serverRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> fileService.browse(999L, "/tmp"));
    }

    // --- ReadFile Tests ---

    @Test
    void readFile_validPath_returnsContent() throws Exception {
        when(sshConnection.executeCommand("tail -n 100 '/var/log/syslog' 2>&1"))
                .thenReturn(new SshConnection.CommandResult(0, "log line 1\nlog line 2\n", ""));

        Map<String, Object> result = fileService.readFile(1L, "/var/log/syslog", 100);

        assertEquals("log line 1\nlog line 2\n", result.get("content"));
        assertEquals(100, result.get("lines"));
    }

    @Test
    void readFile_linesClamped_toMax10000() throws Exception {
        when(sshConnection.executeCommand("tail -n 10000 '/var/log/big.log' 2>&1"))
                .thenReturn(new SshConnection.CommandResult(0, "content", ""));

        fileService.readFile(1L, "/var/log/big.log", 50000);

        verify(sshConnection).executeCommand("tail -n 10000 '/var/log/big.log' 2>&1");
    }

    @Test
    void readFile_linesClamped_toMin1() throws Exception {
        when(sshConnection.executeCommand("tail -n 1 '/var/log/small.log' 2>&1"))
                .thenReturn(new SshConnection.CommandResult(0, "content", ""));

        fileService.readFile(1L, "/var/log/small.log", 0);

        verify(sshConnection).executeCommand("tail -n 1 '/var/log/small.log' 2>&1");
    }

    // --- Stat Tests ---

    @Test
    void stat_validPath_parsesOutput() throws Exception {
        when(sshConnection.executeCommand("stat --format='%U|%G|%s|%Y|%A' '/tmp/file' 2>&1"))
                .thenReturn(new SshConnection.CommandResult(0, "root|root|1024|1705312200|-rw-r--r--", ""));

        Map<String, String> result = fileService.stat(1L, "/tmp/file");

        assertEquals("root", result.get("owner"));
        assertEquals("root", result.get("group"));
        assertEquals("1024", result.get("size"));
        assertEquals("1705312200", result.get("modified"));
        assertEquals("-rw-r--r--", result.get("permissions"));
    }

    @Test
    void stat_sshFails_returnsError() throws Exception {
        when(sshConnection.executeCommand(anyString()))
                .thenReturn(new SshConnection.CommandResult(1, "", "No such file"));

        Map<String, String> result = fileService.stat(1L, "/nonexistent");
        assertTrue(result.containsKey("error"));
    }

    // --- Mkdir Tests ---

    @Test
    void mkdir_validPath_callsMkdirP() throws Exception {
        when(sshConnection.executeCommand("mkdir -p '/tmp/newdir'"))
                .thenReturn(new SshConnection.CommandResult(0, "", ""));

        assertDoesNotThrow(() -> fileService.mkdir(1L, "/tmp/newdir"));
    }

    @Test
    void mkdir_pathTraversal_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> fileService.mkdir(1L, "../../evil"));
    }
}
