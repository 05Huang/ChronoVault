package com.chronovault.repository;

import com.chronovault.entity.Server;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ServerRepository extends JpaRepository<Server, Long> {
    List<Server> findByUserId(Long userId);
    long countByStatus(Server.ServerStatus status);
    Server findByAgentId(String agentId);

    /**
     * Find servers with auto-snapshot enabled and running — avoids loading all servers.
     */
    List<Server> findByAutoSnapshotEnabledTrueAndStatus(Server.ServerStatus status);

    /**
     * Find servers belonging to a specific group — avoids loading all servers for group deletion.
     */
    List<Server> findByGroupId(Long groupId);

    /**
     * Check if a server with the given IP already exists — avoids loading all servers.
     */
    boolean existsByIp(String ip);
}
