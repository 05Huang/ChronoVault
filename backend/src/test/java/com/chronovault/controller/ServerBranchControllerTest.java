package com.chronovault.controller;

import com.chronovault.dto.branch.CreateBranchRequest;
import com.chronovault.dto.branch.ServerBranchDTO;
import com.chronovault.entity.User;
import com.chronovault.service.ServerBranchService;
import com.chronovault.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServerBranchControllerTest {

    @Mock private ServerBranchService branchService;
    @Mock private UserService userService;

    @InjectMocks
    private ServerBranchController controller;

    @Test
    void getBranches_returnsList() {
        when(branchService.getBranches(1L)).thenReturn(List.of());
        var response = controller.getBranches(1L);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void createBranch_succeeds() {
        User user = User.builder().id(1L).email("test@test.com").build();
        when(userService.getByEmail("test@test.com")).thenReturn(user);
        ServerBranchDTO branch = new ServerBranchDTO(1L, "main", "desc", 1L, 1L, false, "2026-01-01T00:00:00");
        when(branchService.createBranch(eq(1L), any(CreateBranchRequest.class), eq(1L))).thenReturn(branch);

        var auth = new UsernamePasswordAuthenticationToken("test@test.com", null, List.of());
        CreateBranchRequest request = new CreateBranchRequest("main", "desc", 1L);
        var response = controller.createBranch(auth, 1L, request);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void deleteBranch_succeeds() {
        doNothing().when(branchService).deleteBranch(1L, 1L);
        var response = controller.deleteBranch(1L, 1L);
        assertEquals(200, response.getStatusCode().value());
        verify(branchService).deleteBranch(1L, 1L);
    }
}