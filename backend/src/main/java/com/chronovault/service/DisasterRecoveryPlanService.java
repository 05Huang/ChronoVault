package com.chronovault.service;

import com.chronovault.entity.DisasterRecoveryPlan;
import com.chronovault.entity.Server;
import com.chronovault.exception.BadRequestException;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.DisasterRecoveryPlanRepository;
import com.chronovault.repository.ServerRepository;
import com.chronovault.ssh.SshConnection;
import com.chronovault.ssh.SshConnectionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DisasterRecoveryPlanService {

    private final DisasterRecoveryPlanRepository planRepository;
    private final ServerRepository serverRepository;
    private final SshConnectionManager sshManager;

    @Transactional(readOnly = true)
    public List<DisasterRecoveryPlan> getPlans() {
        return planRepository.findAll();
    }

    @Transactional(readOnly = true)
    public DisasterRecoveryPlan getPlan(Long id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("恢复计划不存在: " + id));
    }

    @Transactional
    public DisasterRecoveryPlan createPlan(DisasterRecoveryPlan plan) {
        return planRepository.save(plan);
    }

    @Transactional
    public DisasterRecoveryPlan updatePlan(Long id, DisasterRecoveryPlan updates) {
        DisasterRecoveryPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("恢复计划不存在: " + id));
        if (updates.getName() != null) plan.setName(updates.getName());
        if (updates.getDescription() != null) plan.setDescription(updates.getDescription());
        if (updates.getSteps() != null) plan.setSteps(updates.getSteps());
        if (updates.getEstimatedRto() != null) plan.setEstimatedRto(updates.getEstimatedRto());
        if (updates.getEstimatedRpo() != null) plan.setEstimatedRpo(updates.getEstimatedRpo());
        if (updates.getStatus() != null) plan.setStatus(updates.getStatus());
        return planRepository.save(plan);
    }

    @Transactional
    public void deletePlan(Long id) {
        planRepository.deleteById(id);
    }

    /**
     * Get pre-defined disaster recovery playbook templates.
     * Returns common scenarios for Web servers, Database servers, and Cache servers.
     */
    public List<Map<String, Object>> getPlaybookTemplates() {
        return List.of(
                getWebServerTemplate(),
                getDatabaseServerTemplate(),
                getCacheServerTemplate()
        );
    }

    private Map<String, Object> getWebServerTemplate() {
        return Map.of(
                "id", "web-server",
                "name", "Web 服务器恢复模板",
                "description", "适用于 Nginx/Apache 等 Web 服务器的灾难恢复步骤",
                "estimatedRto", 15,
                "estimatedRpo", 5,
                "steps", "1. 从最新快照恢复 Web 根目录文件\n" +
                        "2. 检查并恢复 Nginx/Apache 配置文件\n" +
                        "3. 重启 Web 服务: systemctl restart nginx\n" +
                        "4. 验证服务状态: systemctl status nginx\n" +
                        "5. 检查端口监听: ss -tlnp | grep :80\n" +
                        "6. 验证 HTTP 响应: curl -s -o /dev/null -w '%{http_code}' http://localhost"
        );
    }

    private Map<String, Object> getDatabaseServerTemplate() {
        return Map.of(
                "id", "database-server",
                "name", "数据库服务器恢复模板",
                "description", "适用于 MySQL/PostgreSQL 等数据库服务器的灾难恢复步骤",
                "estimatedRto", 30,
                "estimatedRpo", 1,
                "steps", "1. 停止数据库服务: systemctl stop mysql\n" +
                        "2. 从最新快照恢复数据库数据目录\n" +
                        "3. 恢复数据库配置文件\n" +
                        "4. 启动数据库服务: systemctl start mysql\n" +
                        "5. 检查数据库状态: systemctl status mysql\n" +
                        "6. 执行完整性检查: mysqlcheck --all-databases\n" +
                        "7. 验证连接: mysql -u root -e 'SELECT 1'"
        );
    }

    private Map<String, Object> getCacheServerTemplate() {
        return Map.of(
                "id", "cache-server",
                "name", "缓存服务器恢复模板",
                "description", "适用于 Redis/Memcached 等缓存服务器的灾难恢复步骤",
                "estimatedRto", 5,
                "estimatedRpo", 0,
                "steps", "1. 检查缓存服务状态: systemctl status redis\n" +
                        "2. 如果服务停止，重启服务: systemctl start redis\n" +
                        "3. 恢复 Redis 配置文件 (redis.conf)\n" +
                        "4. 重启 Redis 服务: systemctl restart redis\n" +
                        "5. 验证连接: redis-cli ping\n" +
                        "6. 检查内存使用: redis-cli info memory"
        );
    }

    /**
     * Execute a recovery plan — mark as executed.
     * In a real implementation, this would trigger actual recovery steps.
     */
    @Transactional
    public DisasterRecoveryPlan executePlan(Long id) {
        DisasterRecoveryPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("恢复计划不存在: " + id));
        plan.setLastExecutedAt(LocalDateTime.now());
        plan.setStatus(DisasterRecoveryPlan.PlanStatus.ACTIVE);
        log.info("[DR_EXECUTE] [plan={}] Recovery plan executed: {}", id, plan.getName());
        return planRepository.save(plan);
    }

    /**
     * Simulate a disaster recovery plan execution.
     * Validates the plan and simulates each step without actually performing recovery.
     * Returns a simulation report with step results.
     */
    public Map<String, Object> simulatePlan(Long planId) {
        DisasterRecoveryPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("恢复计划不存在: " + planId));

        if (plan.getSteps() == null || plan.getSteps().isBlank()) {
            throw new BadRequestException("恢复计划没有定义步骤");
        }

        log.info("[DR_SIMULATE] [plan={}] Starting simulation for: {}", planId, plan.getName());

        String[] steps = plan.getSteps().split("\\n");
        List<Map<String, Object>> stepResults = new ArrayList<>();
        int successCount = 0;

        for (int i = 0; i < steps.length; i++) {
            String step = steps[i].trim();
            if (step.isBlank()) continue;

            // Simulate each step — in a real implementation this would validate
            // that the step can be executed (e.g., check if service exists, file exists)
            boolean canExecute = validateStep(step);
            String status = canExecute ? "SIMULATED" : "SKIPPED";

            if (canExecute) successCount++;

            stepResults.add(Map.of(
                    "step", i + 1,
                    "description", step,
                    "status", status,
                    "simulatedAt", LocalDateTime.now().toString()
            ));
        }

        log.info("[DR_SIMULATE] [plan={}] Simulation complete: {}/{} steps can execute",
                planId, successCount, stepResults.size());

        return Map.of(
                "planId", planId,
                "planName", plan.getName(),
                "totalSteps", stepResults.size(),
                "executableSteps", successCount,
                "estimatedRto", plan.getEstimatedRto() != null ? plan.getEstimatedRto() : 0,
                "estimatedRpo", plan.getEstimatedRpo() != null ? plan.getEstimatedRpo() : 0,
                "steps", stepResults
        );
    }

    /**
     * Validate if a single step can be executed.
     * In a real implementation, this would check prerequisites.
     */
    private boolean validateStep(String step) {
        String lower = step.toLowerCase();
        // Simulate validation: check if step references known operations
        return lower.contains("restore") || lower.contains("restart") || lower.contains("check")
                || lower.contains("verify") || lower.contains("backup") || lower.contains("ssh")
                || lower.contains("docker") || lower.contains("systemctl") || lower.contains("install");
    }

    /**
     * Execute a custom script on a target server via SSH.
     * The script is split into individual commands and executed sequentially.
     * Returns execution results for each command.
     */
    public Map<String, Object> executeScript(Long planId, Long serverId) {
        DisasterRecoveryPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("恢复计划不存在: " + planId));
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new ResourceNotFoundException("服务器不存在: " + serverId));

        if (plan.getSteps() == null || plan.getSteps().isBlank()) {
            throw new BadRequestException("恢复计划没有定义脚本步骤");
        }

        log.info("[DR_SCRIPT] [plan={}] [server={}] Executing script on {}", planId, serverId, server.getName());

        SshConnection conn;
        try {
            conn = sshManager.getConnection(server);
        } catch (Exception e) {
            throw new BadRequestException("无法连接到服务器: " + e.getMessage());
        }

        String[] commands = plan.getSteps().split("\\n");
        List<Map<String, Object>> results = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        for (int i = 0; i < commands.length; i++) {
            String command = commands[i].trim();
            if (command.isBlank()) continue;

            try {
                SshConnection.CommandResult result = conn.executeCommand(command,
                        java.time.Duration.ofMinutes(5));
                boolean success = result.isSuccess();
                if (success) successCount++; else failCount++;

                results.add(Map.of(
                        "step", i + 1,
                        "command", command,
                        "success", success,
                        "exitCode", result.exitCode(),
                        "stdout", result.output() != null ? result.output().substring(0, Math.min(500, result.output().length())) : "",
                        "stderr", result.stderr() != null ? result.stderr().substring(0, Math.min(500, result.stderr().length())) : ""
                ));

                log.info("[DR_SCRIPT] [plan={}] Step {} {}: exitCode={}",
                        planId, i + 1, success ? "SUCCESS" : "FAILED", result.exitCode());
            } catch (Exception e) {
                failCount++;
                results.add(Map.of(
                        "step", i + 1,
                        "command", command,
                        "success", false,
                        "exitCode", -1,
                        "stdout", "",
                        "error", e.getMessage() != null ? e.getMessage().substring(0, Math.min(200, e.getMessage().length())) : "Unknown error"
                ));
                log.warn("[DR_SCRIPT] [plan={}] Step {} failed: {}", planId, i + 1, e.getMessage());
            }
        }

        log.info("[DR_SCRIPT] [plan={}] [server={}] Script execution complete: {}/{} succeeded",
                planId, serverId, successCount, successCount + failCount);

        return Map.of(
                "planId", planId,
                "planName", plan.getName(),
                "serverId", serverId,
                "serverName", server.getName(),
                "totalCommands", successCount + failCount,
                "successCount", successCount,
                "failCount", failCount,
                "results", results
        );
    }
}