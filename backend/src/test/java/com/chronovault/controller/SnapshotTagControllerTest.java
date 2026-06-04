package com.chronovault.controller;

import com.chronovault.dto.snapshot.CreateTagRequest;
import com.chronovault.dto.snapshot.SnapshotTagDTO;
import com.chronovault.entity.User;
import com.chronovault.service.SnapshotTagService;
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
class SnapshotTagControllerTest {

    @Mock private SnapshotTagService tagService;
    @Mock private UserService userService;

    @InjectMocks
    private SnapshotTagController controller;

    @Test
    void getTags_returnsList() {
        when(tagService.getTagsBySnapshot(1L)).thenReturn(List.of());
        var response = controller.getTags(1L);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void addTag_succeeds() {
        User user = User.builder().id(1L).email("test@test.com").build();
        when(userService.getByEmail("test@test.com")).thenReturn(user);
        SnapshotTagDTO tag = new SnapshotTagDTO(1L, 1L, "important", "#ff0000", "2026-01-01T00:00:00");
        when(tagService.addTag(eq(1L), any(CreateTagRequest.class), eq(1L))).thenReturn(tag);
        var auth = new UsernamePasswordAuthenticationToken("test@test.com", null, List.of());
        CreateTagRequest request = new CreateTagRequest("important", "#ff0000");
        var response = controller.addTag(1L, auth, request);
        assertEquals(201, response.getStatusCode().value());
    }

    @Test
    void removeTag_succeeds() {
        doNothing().when(tagService).removeTag(1L, "important");
        var response = controller.removeTag(1L, "important");
        assertEquals(200, response.getStatusCode().value());
        verify(tagService).removeTag(1L, "important");
    }
}