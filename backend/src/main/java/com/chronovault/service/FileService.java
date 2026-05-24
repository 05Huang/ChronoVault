package com.chronovault.service;

import com.chronovault.entity.Server;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.ServerRepository;
import com.chronovault.ssh.SshConnection;
import com.chronovault.ssh.SshConnectionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final ServerRepository serverRepository;
    private final SshConnectionManager sshManager;

    // Path traversal protection
    private static final Pattern SAFE_PATH_PATTERN = Pattern.compile("^[a-zA-Z0-9_/\\-.\\s]+$");
    private static final Pattern DANGEROUS_PATH = Pattern.compile("\\.\\./|~");

    private void validatePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("路径不能为空");
        }
        if (DANGEROUS_PATH.matcher(path).find()) {
            throw new IllegalArgumentException("路径包含非法字符: " + path);
        }
        if (!SAFE_PATH_PATTERN.matcher(path).matches()) {
            throw new IllegalArgumentException("路径包含非法字符: " + path);
        }
    }

    private String escapePath(String path) {
        // Escape spaces and special chars for shell commands
        return "'" + path.replace("'", "'\\''") + "'";
    }

    private Server getServer(Long serverId) {
        return serverRepository.findById(serverId)
                .orElseThrow(() -> new ResourceNotFoundException("服务器不存在: " + serverId));
    }

    public List<Map<String, String>> browse(Long serverId, String path) {
        validatePath(path);
        Server server = getServer(serverId);
        try {
            SshConnection conn = sshManager.getConnection(server);
            // Use ls -la for detailed listing, --time-style=long-iso for parseable dates
            SshConnection.CommandResult result = conn.executeCommand(
                    "ls -la --time-style=long-iso " + escapePath(path) + " 2>/dev/null");
            if (!result.isSuccess()) {
                return List.of();
            }

            List<Map<String, String>> entries = new ArrayList<>();
            for (String line : result.stdout().lines().toList()) {
                if (line.startsWith("total") || line.isBlank()) continue;
                // Parse: permissions links owner group size date time name
                String[] parts = line.trim().split("\\s+", 8);
                if (parts.length < 8) continue;
                Map<String, String> entry = new LinkedHashMap<>();
                entry.put("permissions", parts[0]);
                entry.put("owner", parts[2]);
                entry.put("group", parts[3]);
                entry.put("size", parts[4]);
                entry.put("date", parts[5] + " " + parts[6]);
                entry.put("name", parts[7]);
                entry.put("isDir", parts[0].startsWith("d") ? "true" : "false");
                entry.put("isLink", parts[0].startsWith("l") ? "true" : "false");
                entries.add(entry);
            }
            return entries;
        } catch (Exception e) {
            log.warn("Failed to browse {} on {}: {}", path, server.getIp(), e.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> readFile(Long serverId, String path, int lines) {
        validatePath(path);
        Server server = getServer(serverId);
        try {
            SshConnection conn = sshManager.getConnection(server);
            int safeLines = Math.min(Math.max(lines, 1), 10000);
            SshConnection.CommandResult result = conn.executeCommand(
                    "tail -n " + safeLines + " " + escapePath(path) + " 2>&1");
            if (!result.isSuccess()) {
                return Map.of("content", "", "error", result.stderr());
            }
            return Map.of("content", result.stdout(), "lines", safeLines);
        } catch (Exception e) {
            log.warn("Failed to read {} on {}: {}", path, server.getIp(), e.getMessage());
            return Map.of("content", "", "error", e.getMessage());
        }
    }

    public byte[] downloadFile(Long serverId, String path) {
        validatePath(path);
        Server server = getServer(serverId);
        try {
            SshConnection conn = sshManager.getConnection(server);
            // Use cat to read file content as bytes
            SshConnection.CommandResult result = conn.executeCommand("cat " + escapePath(path) + " 2>&1");
            if (!result.isSuccess()) {
                throw new IOException("下载失败: " + result.stderr());
            }
            return result.stdout().getBytes(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("下载文件失败: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> uploadFile(Long serverId, String targetDir, MultipartFile file) {
        validatePath(targetDir);
        Server server = getServer(serverId);
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        // Sanitize filename
        filename = filename.replaceAll("[^a-zA-Z0-9_.\\-]", "_");
        String remotePath = targetDir.endsWith("/") ? targetDir + filename : targetDir + "/" + filename;

        try {
            SshConnection conn = sshManager.getConnection(server);
            // Write file content via SSH stdin
            byte[] content = file.getBytes();
            // Use base64 encoding for safe binary transfer
            String b64 = Base64.getEncoder().encodeToString(content);
            SshConnection.CommandResult result = conn.executeCommand(
                    "echo '" + b64 + "' | base64 -d > " + escapePath(remotePath));
            if (!result.isSuccess()) {
                throw new IOException("上传失败: " + result.stderr());
            }
            log.info("File uploaded to {} on server {}", remotePath, server.getIp());
            return Map.of("success", true, "path", remotePath, "size", content.length);
        } catch (Exception e) {
            throw new RuntimeException("上传文件失败: " + e.getMessage(), e);
        }
    }

    public void deleteFile(Long serverId, String path, boolean recursive) {
        validatePath(path);
        Server server = getServer(serverId);
        try {
            SshConnection conn = sshManager.getConnection(server);
            String cmd = recursive ? "rm -rf " + escapePath(path) : "rm " + escapePath(path);
            SshConnection.CommandResult result = conn.executeCommand(cmd);
            if (!result.isSuccess()) {
                throw new RuntimeException("删除失败: " + result.stderr());
            }
            log.info("File {} deleted on server {}", path, server.getIp());
        } catch (Exception e) {
            throw new RuntimeException("删除文件失败: " + e.getMessage(), e);
        }
    }

    public void mkdir(Long serverId, String path) {
        validatePath(path);
        Server server = getServer(serverId);
        try {
            SshConnection conn = sshManager.getConnection(server);
            SshConnection.CommandResult result = conn.executeCommand("mkdir -p " + escapePath(path));
            if (!result.isSuccess()) {
                throw new RuntimeException("创建目录失败: " + result.stderr());
            }
        } catch (Exception e) {
            throw new RuntimeException("创建目录失败: " + e.getMessage(), e);
        }
    }

    public void chmod(Long serverId, String path, String mode) {
        validatePath(path);
        // Validate chmod mode: must be octal (e.g. 755, 644) or symbolic (e.g. u+x)
        if (!mode.matches("^[0-7]{3,4}$") && !mode.matches("^[ugoa]+[+-=][rwx]+$")) {
            throw new IllegalArgumentException("无效的权限模式: " + mode);
        }
        Server server = getServer(serverId);
        try {
            SshConnection conn = sshManager.getConnection(server);
            SshConnection.CommandResult result = conn.executeCommand(
                    "chmod " + mode + " " + escapePath(path));
            if (!result.isSuccess()) {
                throw new RuntimeException("修改权限失败: " + result.stderr());
            }
        } catch (Exception e) {
            throw new RuntimeException("修改权限失败: " + e.getMessage(), e);
        }
    }

    public Map<String, String> stat(Long serverId, String path) {
        validatePath(path);
        Server server = getServer(serverId);
        try {
            SshConnection conn = sshManager.getConnection(server);
            SshConnection.CommandResult result = conn.executeCommand(
                    "stat --format='%U|%G|%s|%Y|%A' " + escapePath(path) + " 2>&1");
            if (!result.isSuccess()) {
                return Map.of("error", result.stderr());
            }
            String[] parts = result.stdout().trim().split("\\|", 5);
            if (parts.length < 5) {
                return Map.of("error", "无法解析文件信息");
            }
            Map<String, String> info = new LinkedHashMap<>();
            info.put("owner", parts[0]);
            info.put("group", parts[1]);
            info.put("size", parts[2]);
            info.put("modified", parts[3]);
            info.put("permissions", parts[4]);
            return info;
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }
}
