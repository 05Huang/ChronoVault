package com.chronovault.controller;

import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/servers/{serverId}/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /**
     * Browse directory contents.
     */
    @GetMapping("/browse")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> browse(
            @PathVariable Long serverId,
            @RequestParam(defaultValue = "/") String path) {
        return ResponseEntity.ok(ApiResponse.success(fileService.browse(serverId, path)));
    }

    /**
     * Read file content (tail last N lines).
     */
    @GetMapping("/read")
    public ResponseEntity<ApiResponse<Map<String, Object>>> readFile(
            @PathVariable Long serverId,
            @RequestParam String path,
            @RequestParam(defaultValue = "100") int lines) {
        return ResponseEntity.ok(ApiResponse.success(fileService.readFile(serverId, path, lines)));
    }

    /**
     * Download a file.
     */
    @GetMapping("/download")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable Long serverId,
            @RequestParam String path) {
        byte[] content = fileService.downloadFile(serverId, path);
        String filename = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        ByteArrayResource resource = new ByteArrayResource(content);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(content.length)
                .body(resource);
    }

    /**
     * Upload a file.
     */
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadFile(
            @PathVariable Long serverId,
            @RequestParam String path,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(fileService.uploadFile(serverId, path, file)));
    }

    /**
     * Delete a file or directory.
     */
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteFile(
            @PathVariable Long serverId,
            @RequestParam String path,
            @RequestParam(defaultValue = "false") boolean recursive) {
        fileService.deleteFile(serverId, path, recursive);
        return ResponseEntity.ok(ApiResponse.successMsg("文件已删除"));
    }

    /**
     * Create a directory.
     */
    @PostMapping("/mkdir")
    public ResponseEntity<ApiResponse<Void>> mkdir(
            @PathVariable Long serverId,
            @RequestParam String path) {
        fileService.mkdir(serverId, path);
        return ResponseEntity.ok(ApiResponse.successMsg("目录已创建"));
    }

    /**
     * Change file permissions.
     */
    @PostMapping("/chmod")
    public ResponseEntity<ApiResponse<Void>> chmod(
            @PathVariable Long serverId,
            @RequestParam String path,
            @RequestParam String mode) {
        fileService.chmod(serverId, path, mode);
        return ResponseEntity.ok(ApiResponse.successMsg("权限已修改"));
    }

    /**
     * Get file info (stat).
     */
    @GetMapping("/stat")
    public ResponseEntity<ApiResponse<Map<String, String>>> stat(
            @PathVariable Long serverId,
            @RequestParam String path) {
        return ResponseEntity.ok(ApiResponse.success(fileService.stat(serverId, path)));
    }
}
