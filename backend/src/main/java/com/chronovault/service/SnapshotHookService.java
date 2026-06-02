package com.chronovault.service;

import com.chronovault.entity.Server;
import com.chronovault.entity.SnapshotHook;
import com.chronovault.exception.BadRequestException;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.ServerRepository;
import com.chronovault.repository.SnapshotHookRepository;
import com.chronovault.ssh.SshConnection;
import com.chronovault.ssh.SshConnectionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotHookService {

    private final SnapshotHookRepository hookRepository;
    private final ServerRepository serverRepository;
    private final SshConnectionManager sshManager;

    @Transactional(readOnly = true)
    public List<SnapshotHook> getHooks(Long serverId) {
        return hookRepository.findByServerIdOrderByOrderIndexAsc(serverId);
    }

    @Transactional
    public SnapshotHook createHook(Long serverId, SnapshotHook hook) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new ResourceNotFoundException("服务器不存在: " + serverId));
        hook.setServer(server);
        return hookRepository.save(hook);
    }

    @Transactional
    public SnapshotHook updateHook(Long serverId, Long hookId, SnapshotHook updates) {
        SnapshotHook hook = hookRepository.findById(hookId)
                .orElseThrow(() -> new ResourceNotFoundException("Hook 不存在: " + hookId));
        if (!hook.getServer().getId().equals(serverId)) {
            throw new BadRequestException("Hook 不属于该服务器");
        }
        if (updates.getName() != null) hook.setName(updates.getName());
        if (updates.getHookType() != null) hook.setHookType(updates.getHookType());
        if (updates.getCommand() != null) hook.setCommand(updates.getCommand());
        if (updates.getTimeoutSeconds() != null) hook.setTimeoutSeconds(updates.getTimeoutSeconds());
        hook.setEnabled(updates.isEnabled());
        if (updates.getOrderIndex() != null) hook.setOrderIndex(updates.getOrderIndex());
        return hookRepository.save(hook);
    }

    @Transactional
    public void deleteHook(Long serverId, Long hookId) {
        SnapshotHook hook = hookRepository.findById(hookId)
                .orElseThrow(() -> new ResourceNotFoundException("Hook 不存在: " + hookId));
        if (!hook.getServer().getId().equals(serverId)) {
            throw new BadRequestException("Hook 不属于该服务器");
        }
        hookRepository.delete(hook);
    }

    /**
     * Execute all enabled hooks of the given type for a server.
     */
    public void executeHooks(SshConnection conn, Long serverId, SnapshotHook.HookType hookType) {
        List<SnapshotHook> hooks = hookRepository.findByServerIdAndHookTypeAndEnabledOrderByOrderIndexAsc(
                serverId, hookType, true);

        for (SnapshotHook hook : hooks) {
            log.info("Executing hook '{}' (type={}) on server {}", hook.getName(), hookType, serverId);
            try {
                SshConnection.CommandResult result = conn.executeCommand(
                        hook.getCommand(),
                        java.time.Duration.ofSeconds(hook.getTimeoutSeconds()));
                if (result.isSuccess()) {
                    log.info("Hook '{}' completed successfully", hook.getName());
                } else {
                    log.warn("Hook '{}' failed (exit={}): {}", hook.getName(), result.exitCode(),
                            result.stderr() != null ? result.stderr().substring(0, Math.min(200, result.stderr().length())) : "");
                }
            } catch (Exception e) {
                log.error("Hook '{}' threw exception: {}", hook.getName(), e.getMessage());
            }
        }
    }
}