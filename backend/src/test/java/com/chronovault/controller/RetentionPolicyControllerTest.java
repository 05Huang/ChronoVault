package com.chronovault.controller;

import com.chronovault.entity.SnapshotRetentionPolicy;
import com.chronovault.entity.User;
import com.chronovault.repository.ServerRepository;
import com.chronovault.repository.UserRepository;
import com.chronovault.service.SnapshotRetentionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetentionPolicyControllerTest {

    @Mock private SnapshotRetentionService retentionService;
    @Mock private ServerRepository serverRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private RetentionPolicyController controller;

    @Test
    void getAll_returnsList() {
        when(retentionService.getAllPolicies()).thenReturn(List.of());
        var response = controller.getAll();
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void createPolicy_succeeds() {
        User user = User.builder().id(1L).email("test@test.com").build();
        com.chronovault.entity.Server server = com.chronovault.entity.Server.builder().id(1L).name("server1").build();
        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(serverRepository.findById(1L)).thenReturn(Optional.of(server));
        SnapshotRetentionPolicy policy = SnapshotRetentionPolicy.builder().id(1L).name("test").build();
        when(retentionService.createPolicy(any(SnapshotRetentionPolicy.class))).thenReturn(policy);

        var request = new RetentionPolicyController.CreateRetentionPolicyRequest(1L, "test", 10, 30, 7);
        org.springframework.security.core.Authentication auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("test@test.com", null, List.of());
        var response = controller.create(request, auth);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void deletePolicy_succeeds() {
        doNothing().when(retentionService).deletePolicy(1L);
        var response = controller.delete(1L);
        assertEquals(200, response.getStatusCode().value());
        verify(retentionService).deletePolicy(1L);
    }

    @Test
    void togglePolicy_succeeds() {
        SnapshotRetentionPolicy policy = SnapshotRetentionPolicy.builder().id(1L).name("test").enabled(false).build();
        when(retentionService.togglePolicy(1L)).thenReturn(policy);
        var response = controller.toggle(1L);
        assertEquals(200, response.getStatusCode().value());
    }
}