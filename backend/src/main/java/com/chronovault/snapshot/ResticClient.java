package com.chronovault.snapshot;

import com.chronovault.entity.StorageTarget;
import com.chronovault.ssh.SshConnection;
import com.chronovault.ssh.SshConnectionManager;
import com.chronovault.util.LogSanitizer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResticClient {

    private final SshConnectionManager sshManager;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    /**
     * Check if restic is installed on the remote server, install it if not.
     * Returns true if restic is available after this call.
     */
    public boolean ensureResticInstalled(SshConnection conn) {
        SshConnection.CommandResult check = conn.executeCommand("which restic 2>/dev/null || echo ''");
        String resticPath = check.stdout().trim();
        if (!resticPath.isEmpty()) {
            SshConnection.CommandResult versionCheck = conn.executeCommand(resticPath + " version 2>&1");
            if (versionCheck.isSuccess() && versionCheck.stdout().contains("restic")) {
                log.info("Restic already installed at {}: {}", resticPath, versionCheck.stdout().trim().lines().findFirst().orElse(""));
                return true;
            }
        }

        log.info("Restic not found, attempting auto-install...");

        // Method 1: Try direct binary download with sudo to /usr/local/bin
        String downloadCmd = "curl -fsSL -o /tmp/restic.bz2 https://github.com/restic/restic/releases/download/v0.16.5/restic_0.16.5_linux_amd64.bz2 2>&1 || " +
                "wget -q -O /tmp/restic.bz2 https://github.com/restic/restic/releases/download/v0.16.5/restic_0.16.5_linux_amd64.bz2 2>&1";
        SshConnection.CommandResult download = conn.executeCommand(downloadCmd, java.time.Duration.ofMinutes(3));

        if (download.isSuccess()) {
            // Try with sudo first
            SshConnection.CommandResult install = conn.executeCommand(
                    "bunzip2 -f /tmp/restic.bz2 && sudo mv /tmp/restic /usr/local/bin/restic && sudo chmod +x /usr/local/bin/restic && /usr/local/bin/restic version 2>&1");
            if (install.isSuccess() && install.stdout().contains("restic")) {
                log.info("Restic installed to /usr/local/bin: {}", install.stdout().trim());
                return true;
            }

            // Try user local bin
            SshConnection.CommandResult install2 = conn.executeCommand(
                    "bunzip2 -f /tmp/restic.bz2 2>/dev/null; mkdir -p ~/.local/bin && mv /tmp/restic ~/.local/bin/restic && chmod +x ~/.local/bin/restic && ~/.local/bin/restic version 2>&1");
            if (install2.isSuccess() && install2.stdout().contains("restic")) {
                log.info("Restic installed to ~/.local/bin: {}", install2.stdout().trim());
                return true;
            }
        }

        // Method 2: Try apt-get (Debian/Ubuntu)
        SshConnection.CommandResult install3 = conn.executeCommand(
                "sudo apt-get update -qq 2>/dev/null && sudo apt-get install -y -qq restic 2>&1", java.time.Duration.ofMinutes(5));
        if (install3.isSuccess()) {
            SshConnection.CommandResult v = conn.executeCommand("restic version 2>&1");
            if (v.isSuccess() && v.stdout().contains("restic")) {
                log.info("Restic installed via apt-get");
                return true;
            }
        }

        // Method 3: Try yum (CentOS/RHEL)
        SshConnection.CommandResult install4 = conn.executeCommand(
                "sudo yum install -y restic 2>&1", java.time.Duration.ofMinutes(5));
        if (install4.isSuccess()) {
            SshConnection.CommandResult v = conn.executeCommand("restic version 2>&1");
            if (v.isSuccess() && v.stdout().contains("restic")) {
                log.info("Restic installed via yum");
                return true;
            }
        }

        log.error("Failed to install restic via all methods");
        return false;
    }

    /**
     * Find the restic binary path on the remote server.
     */
    public String getResticPath(SshConnection conn) {
        SshConnection.CommandResult check = conn.executeCommand("which restic 2>/dev/null || echo ''");
        String path = check.stdout().trim();
        if (!path.isEmpty()) return path;
        // Check common locations
        SshConnection.CommandResult check2 = conn.executeCommand("test -x /usr/local/bin/restic && echo /usr/local/bin/restic || test -x ~/.local/bin/restic && echo ~/.local/bin/restic || echo restic");
        return check2.stdout().trim();
    }

    public boolean init(SshConnection conn, String repoUrl, String password) {
        String restic = getResticPath(conn);
        String cmd = String.format("RESTIC_PASSWORD=%s %s init --repo %s 2>&1", shellEscape(password), restic, shellEscape(repoUrl));
        log.debug("[RESTIC_INIT] command: {}", LogSanitizer.sanitize(cmd));
        long startMs = System.currentTimeMillis();
        SshConnection.CommandResult result = conn.executeCommand(cmd);
        long durationMs = System.currentTimeMillis() - startMs;

        // Restic returns exit code 1 with "already initialized" message when repo exists — this is OK
        String output = result.stdout() != null ? result.stdout() : "";
        if (!result.isSuccess() && output.contains("already initialized")) {
            log.info("[RESTIC_INIT] Repository already initialized at {} ({}ms)", repoUrl, durationMs);
            meterRegistry.timer("restic.operation", "operation", "init", "result", "already_init").record(durationMs, TimeUnit.MILLISECONDS);
            return true;
        }

        if (!result.isSuccess()) {
            log.warn("[RESTIC_INIT] Failed (exit={}, {}ms): {}", result.exitCode(), durationMs,
                    output.substring(0, Math.min(300, output.length())));
            meterRegistry.timer("restic.operation", "operation", "init", "result", "failed").record(durationMs, TimeUnit.MILLISECONDS);
        } else {
            log.info("[RESTIC_INIT] Success at {} ({}ms)", repoUrl, durationMs);
            meterRegistry.timer("restic.operation", "operation", "init", "result", "success").record(durationMs, TimeUnit.MILLISECONDS);
        }
        return result.isSuccess();
    }

    public ResticSnapshot backup(SshConnection conn, String repoUrl, String password,
                                  List<String> paths, List<String> excludes, String parentId) {
        String restic = getResticPath(conn);
        StringBuilder cmd = new StringBuilder();
        cmd.append("RESTIC_PASSWORD=").append(shellEscape(password)).append(" ");
        cmd.append(restic).append(" backup ");
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

        String fullCmd = cmd.toString();
        log.info("[RESTIC_BACKUP] command: {}", LogSanitizer.sanitize(fullCmd));
        long startMs = System.currentTimeMillis();
        SshConnection.CommandResult result = conn.executeCommand(fullCmd, java.time.Duration.ofHours(2));
        long durationMs = System.currentTimeMillis() - startMs;

        // Exit code 0 = success, 3 = partial success (some files unreadable)
        if (result.exitCode() != 0 && result.exitCode() != 3) {
            log.error("[RESTIC_BACKUP] Failed (exit={}, {}ms): stdout=[{}] stderr=[{}]",
                    result.exitCode(), durationMs,
                    result.stdout() != null ? result.stdout().substring(0, Math.min(1000, result.stdout().length())) : "",
                    result.stderr() != null ? result.stderr().substring(0, Math.min(1000, result.stderr().length())) : "");
            meterRegistry.timer("restic.operation", "operation", "backup", "result", "failed").record(durationMs, TimeUnit.MILLISECONDS);
            return null;
        }

        if (result.exitCode() == 3) {
            // Partial success — parse stderr to identify failed files
            log.warn("[RESTIC_BACKUP] Partial success (some files unreadable, {}ms)", durationMs);
            if (result.stderr() != null && !result.stderr().isBlank()) {
                String[] errorLines = result.stderr().lines()
                        .filter(l -> l.contains("error") || l.contains("permission"))
                        .limit(20)
                        .toArray(String[]::new);
                if (errorLines.length > 0) {
                    log.warn("[RESTIC_BACKUP] Failed files ({}): {}", errorLines.length,
                            String.join("; ", errorLines));
                }
            }
            meterRegistry.timer("restic.operation", "operation", "backup", "result", "partial").record(durationMs, TimeUnit.MILLISECONDS);
        } else {
            log.info("[RESTIC_BACKUP] Success (exit=0, {}ms)", durationMs);
            meterRegistry.timer("restic.operation", "operation", "backup", "result", "success").record(durationMs, TimeUnit.MILLISECONDS);
        }

        try {
            // Parse JSON output - restic outputs one JSON object per line
            for (String line : result.stdout().lines().toList()) {
                if (line.contains("\"snapshot_id\"")) {
                    Map<String, Object> data = objectMapper.readValue(line, new TypeReference<>() {});
                    Map<String, Object> summary = (Map<String, Object>) data.get("summary");
                    long filesNew = summary != null ? parseLong(summary.get("files_new")) : 0;
                    long filesChanged = summary != null ? parseLong(summary.get("files_changed")) : 0;
                    long bytesAdded = summary != null ? parseLong(summary.get("bytes_added")) : 0;
                    return new ResticSnapshot(
                            (String) data.get("snapshot_id"),
                            (String) data.get("tree"),
                            parseLong(data.get("total_bytes_processed")),
                            (String) data.get("time"),
                            filesNew, filesChanged, bytesAdded
                    );
                }
            }
            log.warn("No snapshot_id found in restic output (exit={}): {}",
                    result.exitCode(),
                    result.stdout() != null ? result.stdout().substring(0, Math.min(500, result.stdout().length())) : "");
        } catch (Exception e) {
            log.warn("Failed to parse restic backup output: {}", e.getMessage());
        }
        return null;
    }

    /**
     * List files/directories in a snapshot at a given path.
     * Returns the raw output of `restic ls {hash} --path {path} --json`.
     */
    public String listFiles(SshConnection conn, String repoUrl, String password,
                             String snapshotId, String path) {
        String restic = getResticPath(conn);
        String target = path != null && !path.isBlank() ? path : "/";
        String cmd = String.format("RESTIC_PASSWORD=%s %s ls %s --path %s --repo %s 2>&1",
                shellEscape(password), restic, shellEscape(snapshotId),
                shellEscape(target), shellEscape(repoUrl));
        log.info("Restic ls command: {}", cmd);
        SshConnection.CommandResult result = conn.executeCommand(cmd, java.time.Duration.ofMinutes(5));
        return result.isSuccess() ? result.stdout() : "";
    }

    /**
     * Dump a single file's content from a snapshot.
     * Returns the file content as a string.
     */
    public String dumpFile(SshConnection conn, String repoUrl, String password,
                            String snapshotId, String filePath) {
        String restic = getResticPath(conn);
        String cmd = String.format("RESTIC_PASSWORD=%s %s dump %s %s --repo %s 2>&1",
                shellEscape(password), restic, shellEscape(snapshotId),
                shellEscape(filePath), shellEscape(repoUrl));
        log.info("Restic dump command: {}", LogSanitizer.sanitize(cmd));
        SshConnection.CommandResult result = conn.executeCommand(cmd, java.time.Duration.ofMinutes(5));
        return result.isSuccess() ? result.stdout() : "";
    }

    public List<ResticSnapshotInfo> snapshots(SshConnection conn, String repoUrl, String password) {
        String restic = getResticPath(conn);
        log.info("[RESTIC_SNAPSHOTS] listing snapshots");
        long startMs = System.currentTimeMillis();
        SshConnection.CommandResult result = conn.executeCommand(
                String.format("RESTIC_PASSWORD=%s %s snapshots --repo %s --json 2>&1",
                        shellEscape(password), restic, shellEscape(repoUrl)));
        long durationMs = System.currentTimeMillis() - startMs;

        if (!result.isSuccess()) {
            log.warn("[RESTIC_SNAPSHOTS] Failed (exit={}, {}ms)", result.exitCode(), durationMs);
            meterRegistry.timer("restic.operation", "operation", "snapshots", "result", "failed").record(durationMs, TimeUnit.MILLISECONDS);
            return Collections.emptyList();
        }

        try {
            List<ResticSnapshotInfo> list = objectMapper.readValue(result.stdout(), new TypeReference<>() {});
            log.info("[RESTIC_SNAPSHOTS] Found {} snapshots ({}ms)", list.size(), durationMs);
            meterRegistry.timer("restic.operation", "operation", "snapshots", "result", "success").record(durationMs, TimeUnit.MILLISECONDS);
            return list;
        } catch (Exception e) {
            log.warn("[RESTIC_SNAPSHOTS] Failed to parse output ({}ms): {}", durationMs, e.getMessage());
            meterRegistry.timer("restic.operation", "operation", "snapshots", "result", "parse_error").record(durationMs, TimeUnit.MILLISECONDS);
            return Collections.emptyList();
        }
    }

    public boolean restore(SshConnection conn, String repoUrl, String password,
                           String snapshotId, String targetPath) {
        String restic = getResticPath(conn);
        String cmd = String.format("RESTIC_PASSWORD=%s %s restore %s --target %s --repo %s 2>&1",
                shellEscape(password), restic, shellEscape(snapshotId),
                shellEscape(targetPath), shellEscape(repoUrl));
        log.info("[RESTIC_RESTORE] command: {}", LogSanitizer.sanitize(cmd));
        long startMs = System.currentTimeMillis();
        SshConnection.CommandResult result = conn.executeCommand(cmd, java.time.Duration.ofHours(2));
        long durationMs = System.currentTimeMillis() - startMs;
        if (result.isSuccess()) {
            log.info("[RESTIC_RESTORE] Success ({}ms)", durationMs);
            meterRegistry.timer("restic.operation", "operation", "restore", "result", "success").record(durationMs, TimeUnit.MILLISECONDS);
        } else {
            log.warn("[RESTIC_RESTORE] Failed (exit={}, {}ms)", result.exitCode(), durationMs);
            meterRegistry.timer("restic.operation", "operation", "restore", "result", "failed").record(durationMs, TimeUnit.MILLISECONDS);
        }
        return result.isSuccess();
    }

    /**
     * Restore specific files from a snapshot using --include flags.
     * Only restores the listed paths (files/directories) to the target location.
     */
    public boolean restoreSelective(SshConnection conn, String repoUrl, String password,
                                    String snapshotId, List<String> includePaths, String targetPath) {
        String restic = getResticPath(conn);
        StringBuilder cmd = new StringBuilder();
        cmd.append("RESTIC_PASSWORD=").append(shellEscape(password)).append(" ");
        cmd.append(restic).append(" restore ").append(shellEscape(snapshotId)).append(" ");
        cmd.append("--target ").append(shellEscape(targetPath)).append(" ");
        for (String path : includePaths) {
            cmd.append("--include ").append(shellEscape(path)).append(" ");
        }
        cmd.append("--repo ").append(shellEscape(repoUrl)).append(" 2>&1");

        String fullCmd = cmd.toString();
        log.info("[RESTIC_RESTORE_SELECTIVE] command: {}", fullCmd);
        long startMs = System.currentTimeMillis();
        SshConnection.CommandResult result = conn.executeCommand(fullCmd, java.time.Duration.ofHours(2));
        long durationMs = System.currentTimeMillis() - startMs;
        if (result.isSuccess()) {
            log.info("[RESTIC_RESTORE_SELECTIVE] Success ({}ms)", durationMs);
            meterRegistry.timer("restic.operation", "operation", "restore_selective", "result", "success").record(durationMs, TimeUnit.MILLISECONDS);
        } else {
            log.error("[RESTIC_RESTORE_SELECTIVE] Failed (exit={}, {}ms): stdout=[{}] stderr=[{}]",
                    result.exitCode(), durationMs,
                    result.stdout() != null ? result.stdout().substring(0, Math.min(1000, result.stdout().length())) : "",
                    result.stderr() != null ? result.stderr().substring(0, Math.min(1000, result.stderr().length())) : "");
            meterRegistry.timer("restic.operation", "operation", "restore_selective", "result", "failed").record(durationMs, TimeUnit.MILLISECONDS);
        }
        return result.isSuccess();
    }

    /**
     * Restore specific paths from a snapshot directly to the server root (/).
     * This is used for revert operations — it overwrites the target files with
     * the versions from the parent snapshot.
     */
    public boolean restoreToServer(SshConnection conn, String repoUrl, String password,
                                    String snapshotId, List<String> includePaths) {
        String restic = getResticPath(conn);
        StringBuilder cmd = new StringBuilder();
        cmd.append("RESTIC_PASSWORD=").append(shellEscape(password)).append(" ");
        cmd.append(restic).append(" restore ").append(shellEscape(snapshotId)).append(" ");
        cmd.append("--target / ");
        for (String path : includePaths) {
            cmd.append("--include ").append(shellEscape(path)).append(" ");
        }
        cmd.append("--repo ").append(shellEscape(repoUrl)).append(" 2>&1");

        String fullCmd = cmd.toString();
        log.info("Restic restore-to-server command: {}", fullCmd);
        SshConnection.CommandResult result = conn.executeCommand(fullCmd, java.time.Duration.ofHours(2));
        log.info("Restic restore-to-server exitCode={}", result.exitCode());
        if (!result.isSuccess()) {
            log.error("Restic restore-to-server failed (exit={}): stdout=[{}] stderr=[{}]",
                    result.exitCode(),
                    result.stdout() != null ? result.stdout().substring(0, Math.min(1000, result.stdout().length())) : "",
                    result.stderr() != null ? result.stderr().substring(0, Math.min(1000, result.stderr().length())) : "");
        }
        return result.isSuccess();
    }

    public boolean dryRunRestore(SshConnection conn, String repoUrl, String password, String snapshotId) {
        // Use 'restic check' to verify repo integrity (dry-run restore not supported in all versions)
        String restic = getResticPath(conn);
        String cmd = String.format("RESTIC_PASSWORD=%s %s check --repo %s 2>&1",
                shellEscape(password), restic, shellEscape(repoUrl));
        log.info("Restic check command: {}", cmd);
        SshConnection.CommandResult result = conn.executeCommand(cmd, java.time.Duration.ofMinutes(10));
        log.info("Restic check exitCode={}", result.exitCode());
        if (!result.isSuccess()) {
            log.error("Restic check failed (exit={}): {}", result.exitCode(),
                    result.stdout() != null ? result.stdout().substring(0, Math.min(500, result.stdout().length())) : "");
        }
        return result.isSuccess();
    }

    public String diff(SshConnection conn, String repoUrl, String password,
                       String snapshot1, String snapshot2) {
        String restic = getResticPath(conn);
        log.info("[RESTIC_DIFF] diff {} vs {}", snapshot1, snapshot2);
        long startMs = System.currentTimeMillis();
        SshConnection.CommandResult result = conn.executeCommand(
                String.format("RESTIC_PASSWORD=%s %s diff %s %s --repo %s 2>&1",
                        shellEscape(password), restic, shellEscape(snapshot1),
                        shellEscape(snapshot2), shellEscape(repoUrl)));
        long durationMs = System.currentTimeMillis() - startMs;
        if (result.isSuccess()) {
            log.info("[RESTIC_DIFF] Success ({}ms)", durationMs);
            meterRegistry.timer("restic.operation", "operation", "diff", "result", "success").record(durationMs, TimeUnit.MILLISECONDS);
        } else {
            log.warn("[RESTIC_DIFF] Failed (exit={}, {}ms)", result.exitCode(), durationMs);
            meterRegistry.timer("restic.operation", "operation", "diff", "result", "failed").record(durationMs, TimeUnit.MILLISECONDS);
        }
        return result.isSuccess() ? result.stdout() : "";
    }

    /**
     * Dump specific files from a snapshot to a local directory on the server.
     * Used for cherry-pick operations: extracts files then transfers them to target.
     */
    public boolean dumpFiles(SshConnection conn, String repoUrl, String password,
                              String snapshotId, List<String> paths, String targetDir) {
        String restic = getResticPath(conn);
        StringBuilder cmd = new StringBuilder();
        cmd.append("RESTIC_PASSWORD=").append(shellEscape(password)).append(" ");
        cmd.append(restic).append(" dump ").append(shellEscape(snapshotId)).append(" ");
        for (String path : paths) {
            cmd.append(shellEscape(path)).append(" ");
        }
        cmd.append("--repo ").append(shellEscape(repoUrl)).append(" 2>&1 | ");
        cmd.append("sudo tee /dev/null > /dev/null && echo DUMP_OK");

        // Use restore --include to extract to temp dir instead (more reliable)
        String fullCmd = String.format(
                "RESTIC_PASSWORD=%s %s restore %s --include %s --target %s --repo %s 2>&1 && echo RESTORE_OK",
                shellEscape(password), restic, shellEscape(snapshotId),
                shellEscape(String.join(",", paths)),
                shellEscape(targetDir), shellEscape(repoUrl));

        log.info("Restic dump command: {}", fullCmd);
        SshConnection.CommandResult result = conn.executeCommand(fullCmd, java.time.Duration.ofHours(1));
        log.info("Restic dump exitCode={}", result.exitCode());
        return result.isSuccess() || (result.stdout() != null && result.stdout().contains("RESTORE_OK"));
    }

    /**
     * Copy files from one directory to another on the same server using cp.
     */
    public boolean copyFiles(SshConnection conn, String sourceDir, List<String> paths, String destDir) {
        StringBuilder cmd = new StringBuilder();
        cmd.append("sudo mkdir -p ").append(shellEscape(destDir)).append(" && ");
        for (String path : paths) {
            // Resolve the file within the source directory structure
            cmd.append("sudo cp --parents ").append(shellEscape(sourceDir + path)).append(" ").append(shellEscape("/")).append(" 2>/dev/null || ");
            cmd.append("sudo cp ").append(shellEscape(sourceDir + path)).append(" ").append(shellEscape(destDir)).append(" 2>/dev/null || ");
        }
        cmd.append("true");

        String fullCmd = cmd.toString();
        log.info("Copy files command: {}", fullCmd);
        SshConnection.CommandResult result = conn.executeCommand(fullCmd, java.time.Duration.ofMinutes(10));
        return result.isSuccess();
    }

    public boolean check(SshConnection conn, String repoUrl, String password) {
        String restic = getResticPath(conn);
        log.info("[RESTIC_CHECK] checking repository integrity");
        long startMs = System.currentTimeMillis();
        SshConnection.CommandResult result = conn.executeCommand(
                String.format("RESTIC_PASSWORD=%s %s check --repo %s 2>&1",
                        shellEscape(password), restic, shellEscape(repoUrl)));
        long durationMs = System.currentTimeMillis() - startMs;
        if (result.isSuccess()) {
            log.info("[RESTIC_CHECK] Integrity OK ({}ms)", durationMs);
            meterRegistry.timer("restic.operation", "operation", "check", "result", "success").record(durationMs, TimeUnit.MILLISECONDS);
        } else {
            log.warn("[RESTIC_CHECK] Failed (exit={}, {}ms)", result.exitCode(), durationMs);
            meterRegistry.timer("restic.operation", "operation", "check", "result", "failed").record(durationMs, TimeUnit.MILLISECONDS);
        }
        return result.isSuccess();
    }

    /**
     * Copy a snapshot from one restic repository to another using `restic copy`.
     * Both repos must be accessible from the same server.
     */
    public boolean copySnapshot(SshConnection conn, String sourceRepoUrl, String sourcePassword,
                                 String targetRepoUrl, String targetPassword, String snapshotId) {
        String restic = getResticPath(conn);
        String cmd = String.format(
                "RESTIC_PASSWORD=%s %s copy --repo %s --repo2 %s --password-command 'echo %s' %s 2>&1",
                shellEscape(sourcePassword), restic, shellEscape(sourceRepoUrl),
                shellEscape(targetRepoUrl), shellEscape(targetPassword), shellEscape(snapshotId));
        log.info("[RESTIC_COPY] snapshot {} to {}", snapshotId, targetRepoUrl);
        long startMs = System.currentTimeMillis();
        SshConnection.CommandResult result = conn.executeCommand(cmd, java.time.Duration.ofHours(2));
        long durationMs = System.currentTimeMillis() - startMs;
        if (result.isSuccess()) {
            log.info("[RESTIC_COPY] Success ({}ms)", durationMs);
            meterRegistry.timer("restic.operation", "operation", "copy", "result", "success").record(durationMs, TimeUnit.MILLISECONDS);
        } else {
            log.error("[RESTIC_COPY] Failed (exit={}, {}ms): stdout=[{}] stderr=[{}]",
                    result.exitCode(), durationMs,
                    result.stdout() != null ? result.stdout().substring(0, Math.min(1000, result.stdout().length())) : "",
                    result.stderr() != null ? result.stderr().substring(0, Math.min(1000, result.stderr().length())) : "");
            meterRegistry.timer("restic.operation", "operation", "copy", "result", "failed").record(durationMs, TimeUnit.MILLISECONDS);
        }
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

    public record ResticSnapshot(String snapshotId, String tree, long totalBytesProcessed, String time,
                                  long filesNew, long filesChanged, long bytesAdded) {}

    public record ResticSnapshotInfo(String id, String time, String tree, Map<String, Object> summary) {}
}
