package com.chronovault.snapshot;

import com.chronovault.entity.StorageTarget;
import com.chronovault.ssh.SshConnection;
import com.chronovault.ssh.SshConnectionManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResticClient {

    private final SshConnectionManager sshManager;
    private final ObjectMapper objectMapper;

    public boolean init(SshConnection conn, String repoUrl, String password) {
        SshConnection.CommandResult result = conn.executeCommand(
                String.format("RESTIC_PASSWORD=%s restic init --repo %s 2>&1", shellEscape(password), shellEscape(repoUrl)));
        return result.isSuccess();
    }

    public ResticSnapshot backup(SshConnection conn, String repoUrl, String password,
                                  List<String> paths, List<String> excludes, String parentId) {
        StringBuilder cmd = new StringBuilder();
        cmd.append("RESTIC_PASSWORD=").append(shellEscape(password)).append(" ");
        cmd.append("restic backup ");
        for (String path : paths) {
            cmd.append(shellEscape(path)).append(" ");
        }
        for (String exclude : excludes) {
            cmd.append("--exclude ").append(shellEscape(exclude)).append(" ");
        }
        if (parentId != null) {
            cmd.append("--parent ").append(shellEscape(parentId)).append(" ");
        }
        cmd.append("--repo ").append(shellEscape(repoUrl)).append(" ");
        cmd.append("--json 2>&1");

        SshConnection.CommandResult result = conn.executeCommand(cmd.toString(), java.time.Duration.ofHours(2));
        if (!result.isSuccess()) {
            log.error("Restic backup failed: {}", result.stderr());
            return null;
        }

        try {
            // Parse JSON output - restic outputs one JSON object per line
            for (String line : result.stdout().lines().toList()) {
                if (line.contains("\"snapshot_id\"")) {
                    Map<String, Object> data = objectMapper.readValue(line, new TypeReference<>() {});
                    return new ResticSnapshot(
                            (String) data.get("snapshot_id"),
                            (String) data.get("tree"),
                            parseLong(data.get("total_bytes_processed")),
                            (String) data.get("time")
                    );
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse restic backup output: {}", e.getMessage());
        }
        return null;
    }

    public List<ResticSnapshotInfo> snapshots(SshConnection conn, String repoUrl, String password) {
        SshConnection.CommandResult result = conn.executeCommand(
                String.format("RESTIC_PASSWORD=%s restic snapshots --repo %s --json 2>&1",
                        shellEscape(password), shellEscape(repoUrl)));

        if (!result.isSuccess()) {
            return Collections.emptyList();
        }

        try {
            return objectMapper.readValue(result.stdout(), new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse restic snapshots: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public boolean restore(SshConnection conn, String repoUrl, String password,
                           String snapshotId, String targetPath) {
        SshConnection.CommandResult result = conn.executeCommand(
                String.format("RESTIC_PASSWORD=%s restic restore %s --target %s --repo %s 2>&1",
                        shellEscape(password), shellEscape(snapshotId),
                        shellEscape(targetPath), shellEscape(repoUrl)),
                java.time.Duration.ofHours(2));
        return result.isSuccess();
    }

    public boolean dryRunRestore(SshConnection conn, String repoUrl, String password, String snapshotId) {
        SshConnection.CommandResult result = conn.executeCommand(
                String.format("RESTIC_PASSWORD=%s restic restore %s --dry-run --repo %s 2>&1",
                        shellEscape(password), shellEscape(snapshotId), shellEscape(repoUrl)));
        return result.isSuccess();
    }

    public String diff(SshConnection conn, String repoUrl, String password,
                       String snapshot1, String snapshot2) {
        SshConnection.CommandResult result = conn.executeCommand(
                String.format("RESTIC_PASSWORD=%s restic diff %s %s --repo %s 2>&1",
                        shellEscape(password), shellEscape(snapshot1),
                        shellEscape(snapshot2), shellEscape(repoUrl)));
        return result.isSuccess() ? result.stdout() : "";
    }

    public boolean check(SshConnection conn, String repoUrl, String password) {
        SshConnection.CommandResult result = conn.executeCommand(
                String.format("RESTIC_PASSWORD=%s restic check --repo %s 2>&1",
                        shellEscape(password), shellEscape(repoUrl)));
        return result.isSuccess();
    }

    public String buildRepoUrl(StorageTarget target) {
        return switch (target.getType()) {
            case LOCAL, BLOCK -> target.getEndpoint();
            case S3 -> "s3:" + target.getEndpoint();
            case OSS -> "s3:oss-" + target.getEndpoint();
            case WEBDAV -> "rest:" + target.getEndpoint();
            default -> target.getEndpoint();
        };
    }

    private String shellEscape(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private Long parseLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Number) return ((Number) val).longValue();
        try { return Long.parseLong(val.toString()); } catch (Exception e) { return 0L; }
    }

    public record ResticSnapshot(String snapshotId, String tree, long totalBytesProcessed, String time) {}

    public record ResticSnapshotInfo(String id, String time, String tree, Map<String, Object> summary) {}
}
