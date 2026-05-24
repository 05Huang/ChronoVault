package com.chronovault.service;

import com.chronovault.dto.settings.CreateApiKeyResponse;
import com.chronovault.dto.settings.GenerateKeyRequest;
import com.chronovault.entity.Server;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.ServerRepository;
import com.chronovault.ssh.SshConnection;
import com.chronovault.ssh.SshConnectionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentInstallService {

    private final ServerRepository serverRepository;
    private final SshConnectionManager sshManager;
    private final SettingsService settingsService;

    @Value("${chronovault.agent.server-url:}")
    private String configuredServerUrl;

    @Value("${chronovault.agent.binary-path:}")
    private String customBinaryPath;

    public Map<String, Object> installAgent(Long serverId, String currentUserEmail, String requestUrl) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new ResourceNotFoundException("服务器不存在: " + serverId));

        String serverUrl = resolveServerUrl(requestUrl);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("steps", new java.util.ArrayList<String>());

        try {
            SshConnection conn = sshManager.getConnection(server);
            var steps = (java.util.ArrayList<String>) result.get("steps");

            // Step 1: Upload agent binary
            steps.add("上传 Agent 二进制文件...");
            result.put("steps", steps);
            Path tempBinary = extractAgentBinary();
            if (tempBinary == null) {
                result.put("success", false);
                result.put("message", "未找到 Agent 二进制文件。请先编译 agent（cd agent && go build -o chronovault-agent）并将二进制放到 backend/src/main/resources/agent-binaries/chronovault-agent-linux-amd64");
                return result;
            }

            try {
                conn.uploadFile(tempBinary.toString(), "/usr/local/bin/chronovault-agent");
                conn.executeCommand("chmod +x /usr/local/bin/chronovault-agent", Duration.ofSeconds(10));
                steps.add("二进制文件上传完成");
            } finally {
                Files.deleteIfExists(tempBinary);
            }

            // Step 2: Generate API key
            steps.add("生成 API 密钥...");
            result.put("steps", steps);
            CreateApiKeyResponse keyResponse = settingsService.generateKey(currentUserEmail,
                    new GenerateKeyRequest("Agent-" + server.getName(), "ADMIN"));
            String rawKey = keyResponse.key();

            // Step 3: Write agent config
            steps.add("写入 Agent 配置...");
            result.put("steps", steps);
            String agentId = "agent-" + server.getId() + "-" + System.currentTimeMillis();
            String configYaml = String.format(
                    "server_url: \"%s\"\napi_key: \"%s\"\nserver_id: %d\nlisten_port: 9270\nheartbeat_interval: 30\n",
                    serverUrl, rawKey, serverId);
            String configCmd = String.format(
                    "mkdir -p /etc/chronovault && cat > /etc/chronovault/agent.yml <<'AGENT_EOF'\n%sAGENT_EOF", configYaml);
            SshConnection.CommandResult configResult = conn.executeCommand(configCmd, Duration.ofSeconds(10));
            if (!configResult.isSuccess()) {
                result.put("success", false);
                result.put("message", "写入配置失败: " + configResult.stderr());
                return result;
            }

            // Step 4: Register agent
            steps.add("注册 Agent...");
            result.put("steps", steps);
            String registerCmd = String.format(
                    "/usr/local/bin/chronovault-agent register --server-url \"%s\" --api-key \"%s\" --server-id %d",
                    serverUrl, rawKey, serverId);
            SshConnection.CommandResult registerResult = conn.executeCommand(registerCmd, Duration.ofSeconds(30));
            if (!registerResult.isSuccess()) {
                log.warn("Agent register returned non-zero exit: {} - {}", registerResult.exitCode(), registerResult.stderr());
                // Continue anyway - the agent might still work
            }
            steps.add("Agent 注册完成");

            // Step 5: Create systemd service
            steps.add("创建 systemd 服务...");
            result.put("steps", steps);
            String unitFile = "[Unit]\n" +
                    "Description=ChronoVault Agent\n" +
                    "After=network.target\n\n" +
                    "[Service]\n" +
                    "ExecStart=/usr/local/bin/chronovault-agent run\n" +
                    "Restart=always\n" +
                    "RestartSec=5\n\n" +
                    "[Install]\n" +
                    "WantedBy=multi-user.target\n";
            String unitCmd = String.format(
                    "cat > /etc/systemd/system/chronovault-agent.service <<'UNIT_EOF'\n%sUNIT_EOF", unitFile);
            conn.executeCommand(unitCmd, Duration.ofSeconds(10));

            // Step 6: Enable and start service
            steps.add("启动 Agent 服务...");
            result.put("steps", steps);
            conn.executeCommand("systemctl daemon-reload", Duration.ofSeconds(10));
            conn.executeCommand("systemctl enable chronovault-agent", Duration.ofSeconds(10));
            conn.executeCommand("systemctl start chronovault-agent", Duration.ofSeconds(10));

            // Verify it's running
            Thread.sleep(2000);
            SshConnection.CommandResult statusResult = conn.executeCommand("systemctl is-active chronovault-agent", Duration.ofSeconds(5));
            boolean isRunning = "active".equals(statusResult.stdout().trim());

            steps.add(isRunning ? "Agent 服务已启动" : "Agent 服务启动可能失败，请检查日志");
            result.put("steps", steps);
            result.put("success", true);
            result.put("message", isRunning ? "Agent 安装成功！" : "Agent 已安装，但服务可能未正常启动");
            result.put("apiKey", rawKey);
            result.put("agentId", agentId);

        } catch (Exception e) {
            log.error("Agent installation failed for server {}: {}", serverId, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "安装失败: " + e.getMessage());
        }

        return result;
    }

    private Path extractAgentBinary() throws IOException {
        // Try custom path first
        if (customBinaryPath != null && !customBinaryPath.isBlank()) {
            Path custom = Path.of(customBinaryPath);
            if (Files.exists(custom)) return custom;
        }

        // Try classpath resources
        String[] candidates = {
                "agent-binaries/chronovault-agent-linux-amd64",
                "agent-binaries/chronovault-agent-linux-arm64",
                "agent-binaries/chronovault-agent"
        };

        for (String path : candidates) {
            ClassPathResource resource = new ClassPathResource(path);
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    Path temp = Files.createTempFile("chronovault-agent-", "");
                    Files.copy(is, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    temp.toFile().setExecutable(true);
                    log.info("Extracted agent binary from classpath: {}", path);
                    return temp;
                }
            }
        }

        return null;
    }

    private String resolveServerUrl(String requestUrl) {
        if (configuredServerUrl != null && !configuredServerUrl.isBlank()) {
            return configuredServerUrl;
        }
        // Derive from request URL: http://host:port/api/servers/X/install-agent -> http://host:port
        if (requestUrl != null) {
            int apiIdx = requestUrl.indexOf("/api/");
            if (apiIdx > 0) {
                return requestUrl.substring(0, apiIdx);
            }
        }
        return "http://localhost:8080";
    }
}
