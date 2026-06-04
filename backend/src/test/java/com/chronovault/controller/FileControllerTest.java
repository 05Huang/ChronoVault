package com.chronovault.controller;

import com.chronovault.service.FileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileControllerTest {

    @Mock private FileService fileService;

    @InjectMocks
    private FileController controller;

    @Test
    void browse_returnsList() {
        when(fileService.browse(1L, "/")).thenReturn(List.of());
        var response = controller.browse(1L, "/");
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void readFile_returnsContent() {
        when(fileService.readFile(1L, "/etc/nginx/nginx.conf", 100)).thenReturn(Map.of("content", "test"));
        var response = controller.readFile(1L, "/etc/nginx/nginx.conf", 100);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void downloadFile_returnsContent() {
        when(fileService.downloadFile(1L, "/etc/nginx/nginx.conf")).thenReturn(new byte[]{1, 2, 3});
        var response = controller.downloadFile(1L, "/etc/nginx/nginx.conf");
        assertEquals(200, response.getStatusCode().value());
    }
}