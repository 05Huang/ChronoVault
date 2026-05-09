package com.chronovault.ssh;

import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;

import java.io.*;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
public class SshConnection implements AutoCloseable {

    private final ClientSession session;
    private final SshClient client;
    private final String host;
    private final int port;

    public SshConnection(ClientSession session, SshClient client, String host, int port) {
        this.session = session;
        this.client = client;
        this.host = host;
        this.port = port;
    }

    public CommandResult executeCommand(String command) {
        return executeCommand(command, Duration.ofMinutes(1));
    }

    public CommandResult executeCommand(String command, Duration timeout) {
        try (ClientChannel channel = session.createExecChannel(command)) {
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            channel.setOut(stdout);
            channel.setErr(stderr);

            channel.open().verify(timeout.toMillis(), TimeUnit.MILLISECONDS);

            Set<ClientChannelEvent> waitMask = EnumSet.of(ClientChannelEvent.CLOSED);
            channel.waitFor(waitMask, timeout.toMillis());

            int exitCode = channel.getExitStatus() != null ? channel.getExitStatus() : -1;
            return new CommandResult(exitCode, stdout.toString(StandardCharsets.UTF_8),
                    stderr.toString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("SSH command failed on {}:{} - {}: {}", host, port, command, e.getMessage());
            return new CommandResult(-1, "", e.getMessage());
        }
    }

    public void uploadFile(String localPath, String remotePath) throws Exception {
        try (SftpClient sftp = SftpClientFactory.instance().createSftpClient(session)) {
            byte[] data = java.nio.file.Files.readAllBytes(Path.of(localPath));
            try (var out = sftp.write(remotePath)) {
                out.write(data);
            }
        }
    }

    public void downloadFile(String remotePath, String localPath) throws Exception {
        try (SftpClient sftp = SftpClientFactory.instance().createSftpClient(session)) {
            try (var in = sftp.read(remotePath);
                 var out = java.nio.file.Files.newOutputStream(Path.of(localPath))) {
                in.transferTo(out);
            }
        }
    }

    public boolean isOpen() {
        return session != null && session.isOpen();
    }

    @Override
    public void close() {
        try {
            if (session != null && session.isOpen()) {
                session.close();
            }
        } catch (Exception e) {
            log.debug("Error closing SSH session: {}", e.getMessage());
        }
    }

    public record CommandResult(int exitCode, String stdout, String stderr) {
        public boolean isSuccess() { return exitCode == 0; }
        public String output() { return stdout; }
    }
}
