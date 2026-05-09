package com.chronovault.repository;

import com.chronovault.entity.AgentInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgentInfoRepository extends JpaRepository<AgentInfo, Long> {

    Optional<AgentInfo> findByServerId(Long serverId);

    Optional<AgentInfo> findByServerAgentId(String agentId);
}
