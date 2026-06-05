package com.chronovault.service;

import com.chronovault.dto.blame.ChangeAttribution;
import com.chronovault.entity.AuditLog;
import com.chronovault.entity.Server;
import com.chronovault.entity.Snapshot;
import com.chronovault.entity.User;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.AuditLogRepository;
import com.chronovault.repository.ServerRepository;
import com.chronovault.repository.SnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChangeAttributionService {

    private final AuditLogRepository auditLogRepository;
    private final ServerRepository serverRepository;
    private final SnapshotRepository snapshotRepository;

    /**
     * Get blame timeline for a server: who changed what and when.
     * Uses targeted JPQL query instead of loading all audit logs.
     */
    @Transactional(readOnly = true)
    public List<ChangeAttribution> getServerBlame(Long serverId) {
        serverRepository.findById(serverId)
                .orElseThrow(() -> new ResourceNotFoundException("服务器不存在: " + serverId));

        List<AuditLog> logs = auditLogRepository.findByServerId(serverId, PageRequest.of(0, 100)).getContent();

        log.debug("[BLAME] [server={}] Found {} attribution entries", serverId, logs.size());
        return logs.stream().map(ChangeAttribution::from).toList();
    }

    /**
     * Get blame for a specific snapshot: who created it, what changed since previous.
     * Uses targeted JPQL query instead of loading all audit logs.
     */
    @Transactional(readOnly = true)
    public List<ChangeAttribution> getSnapshotBlame(Long snapshotId) {
        snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new ResourceNotFoundException("快照不存在: " + snapshotId));

        List<AuditLog> logs = auditLogRepository.findBySnapshotId(snapshotId, PageRequest.of(0, 50)).getContent();

        log.debug("[BLAME] [snapshot={}] Found {} attribution entries", snapshotId, logs.size());
        return logs.stream().map(ChangeAttribution::from).toList();
    }

    /**
     * Record a blame entry: enrich an audit log with snapshot/server/change_type metadata.
     */
    @Transactional
    public void record(AuditLog.ChangeType changeType, String action, User user,
                       Server server, Snapshot snapshot, Long resourceId, String details) {
        AuditLog entry = AuditLog.builder()
                .user(user)
                .action(action)
                .icon(changeType != null ? changeType.name() : "USER_ACTION")
                .changeType(changeType != null ? changeType.name() : null)
                .server(server)
                .snapshot(snapshot)
                .resourceId(resourceId)
                .details(details)
                .build();
        auditLogRepository.save(entry);
        log.debug("Recorded blame: type={}, action={}, userId={}, serverId={}",
                changeType, action, user != null ? user.getId() : null,
                server != null ? server.getId() : null);
    }
}