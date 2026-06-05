package com.chronovault.dto.server;

import com.chronovault.entity.Volume;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Docker 卷信息 DTO")
public record VolumeDTO(Long id, String name, String path, String type, Long sizeBytes, String size, String status) {
    public static VolumeDTO from(Volume v) {
        String size = v.getSizeBytes() != null ? formatSize(v.getSizeBytes()) : "";
        String path = v.getHostPath() != null ? v.getHostPath() : v.getContainerPath();
        String type = v.getName() != null ? v.getName().toLowerCase() : "data";
        return new VolumeDTO(v.getId(), v.getName(), path, type, v.getSizeBytes(), size, v.getStatus());
    }

    private static String formatSize(long bytes) {
        if (bytes >= 1073741824L) return String.format("%.1f GB", bytes / 1073741824.0);
        if (bytes >= 1048576L) return String.format("%.1f MB", bytes / 1048576.0);
        return bytes + " B";
    }
}
