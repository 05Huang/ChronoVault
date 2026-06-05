package com.chronovault.service;

import com.chronovault.entity.Server;
import com.chronovault.entity.ServerGroup;
import com.chronovault.entity.User;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.ServerGroupRepository;
import com.chronovault.repository.ServerRepository;
import com.chronovault.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServerGroupServiceTest {

    @Mock private ServerGroupRepository groupRepository;
    @Mock private ServerRepository serverRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private ServerGroupService service;

    private User testUser;
    private ServerGroup testGroup;

    @BeforeEach
    void setUp() {
        testUser = User.builder().id(1L).name("Test User").email("test@test.com").build();
        testGroup = ServerGroup.builder().id(1L).name("Production").description("Prod servers").user(testUser).build();
    }

    @Test
    void getGroups_returnsUserGroups() {
        when(groupRepository.findByUserIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(testGroup));
        var result = service.getGroups(1L);
        assertEquals(1, result.size());
        assertEquals("Production", result.get(0).getName());
    }

    @Test
    void createGroup_savesGroup() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(groupRepository.save(any(ServerGroup.class))).thenAnswer(inv -> {
            ServerGroup g = inv.getArgument(0);
            if (g.getId() == null) {
                var field = ServerGroup.class.getDeclaredField("id");
                field.setAccessible(true);
                field.set(g, 1L);
            }
            return g;
        });

        ServerGroup newGroup = ServerGroup.builder().name("Staging").build();
        var result = service.createGroup(1L, newGroup);
        assertNotNull(result);
        verify(groupRepository).save(any(ServerGroup.class));
    }

    @Test
    void createGroup_userNotFound_throwsException() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.createGroup(999L, testGroup));
    }

    @Test
    void deleteGroup_removesServersFromGroup() {
        when(groupRepository.findById(1L)).thenReturn(Optional.of(testGroup));
        Server serverInGroup = Server.builder().id(1L).name("server1").group(testGroup).build();
        when(serverRepository.findByGroupId(1L)).thenReturn(List.of(serverInGroup));

        service.deleteGroup(1L);

        verify(serverRepository).findByGroupId(1L);
        assertNull(serverInGroup.getGroup());
        verify(groupRepository).delete(testGroup);
    }

    @Test
    void deleteGroup_groupNotFound_throwsException() {
        when(groupRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.deleteGroup(999L));
    }

    @Test
    void addServerToGroup_setsGroup() {
        when(groupRepository.findById(1L)).thenReturn(Optional.of(testGroup));
        Server server = Server.builder().id(1L).name("server1").build();
        when(serverRepository.findById(1L)).thenReturn(Optional.of(server));

        service.addServerToGroup(1L, 1L);

        assertEquals(testGroup, server.getGroup());
        verify(serverRepository).save(server);
    }

    @Test
    void removeServerFromGroup_clearsGroup() {
        Server server = Server.builder().id(1L).name("server1").group(testGroup).build();
        when(serverRepository.findById(1L)).thenReturn(Optional.of(server));

        service.removeServerFromGroup(1L);

        assertNull(server.getGroup());
        verify(serverRepository).save(server);
    }

    @Test
    void addServerToGroup_groupNotFound_throwsException() {
        when(groupRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.addServerToGroup(999L, 1L));
    }

    @Test
    void addServerToGroup_serverNotFound_throwsException() {
        when(groupRepository.findById(1L)).thenReturn(Optional.of(testGroup));
        when(serverRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.addServerToGroup(1L, 999L));
    }

    @Test
    void removeServerFromGroup_serverNotFound_throwsException() {
        when(serverRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.removeServerFromGroup(999L));
    }

    @Test
    void updateGroup_groupNotFound_throwsException() {
        when(groupRepository.findById(999L)).thenReturn(Optional.empty());
        ServerGroup updates = ServerGroup.builder().name("New Name").build();
        assertThrows(ResourceNotFoundException.class, () -> service.updateGroup(999L, updates));
    }

    @Test
    void updateGroup_updatesFields() {
        when(groupRepository.findById(1L)).thenReturn(Optional.of(testGroup));
        when(groupRepository.save(any(ServerGroup.class))).thenAnswer(inv -> inv.getArgument(0));

        ServerGroup updates = ServerGroup.builder()
                .name("Updated Name")
                .description("Updated description")
                .environmentType(ServerGroup.EnvironmentType.STAGING)
                .color("#ff5722")
                .build();

        ServerGroup result = service.updateGroup(1L, updates);

        assertEquals("Updated Name", result.getName());
        assertEquals("Updated description", result.getDescription());
        assertEquals(ServerGroup.EnvironmentType.STAGING, result.getEnvironmentType());
        assertEquals("#ff5722", result.getColor());
    }

    @Test
    void updateGroup_partialUpdate_onlyUpdatesProvidedFields() {
        when(groupRepository.findById(1L)).thenReturn(Optional.of(testGroup));
        when(groupRepository.save(any(ServerGroup.class))).thenAnswer(inv -> inv.getArgument(0));

        ServerGroup updates = ServerGroup.builder().name("Only Name").build();

        ServerGroup result = service.updateGroup(1L, updates);

        assertEquals("Only Name", result.getName());
        assertEquals("Prod servers", result.getDescription()); // unchanged
    }
}