package com.chronovault.service;

import com.chronovault.entity.Server;
import com.chronovault.entity.ServerGroup;
import com.chronovault.entity.User;
import com.chronovault.exception.BadRequestException;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.ServerGroupRepository;
import com.chronovault.repository.ServerRepository;
import com.chronovault.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServerGroupService {

    private final ServerGroupRepository groupRepository;
    private final ServerRepository serverRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<ServerGroup> getGroups(Long userId) {
        return groupRepository.findByUserIdOrderByCreatedAtAsc(userId);
    }

    @Transactional
    public ServerGroup createGroup(Long userId, ServerGroup group) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在"));
        group.setUser(user);
        return groupRepository.save(group);
    }

    @Transactional
    public ServerGroup updateGroup(Long groupId, ServerGroup updates) {
        ServerGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("分组不存在: " + groupId));
        if (updates.getName() != null) group.setName(updates.getName());
        if (updates.getDescription() != null) group.setDescription(updates.getDescription());
        if (updates.getEnvironmentType() != null) group.setEnvironmentType(updates.getEnvironmentType());
        if (updates.getColor() != null) group.setColor(updates.getColor());
        return groupRepository.save(group);
    }

    @Transactional
    public void deleteGroup(Long groupId) {
        ServerGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("分组不存在: " + groupId));
        // Use targeted query instead of loading all servers
        List<Server> servers = serverRepository.findByGroupId(groupId);
        for (Server server : servers) {
            server.setGroup(null);
            serverRepository.save(server);
        }
        groupRepository.delete(group);
    }

    @Transactional
    public void addServerToGroup(Long groupId, Long serverId) {
        ServerGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("分组不存在: " + groupId));
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new ResourceNotFoundException("服务器不存在: " + serverId));
        server.setGroup(group);
        serverRepository.save(server);
    }

    @Transactional
    public void removeServerFromGroup(Long serverId) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new ResourceNotFoundException("服务器不存在: " + serverId));
        server.setGroup(null);
        serverRepository.save(server);
    }
}