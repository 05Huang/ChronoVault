package com.chronovault.dto.server;

import com.chronovault.entity.Container;

public record ContainerDTO(Long id, String name, String type, String cpuUsage, String memoryUsage,
                           String memoryPercent, String diskIo, String status, String networks) {
    public static ContainerDTO from(Container c) {
        double cpu = c.getCpuPercent() != null ? c.getCpuPercent() : 0;
        double memPct = c.getMemoryPercent() != null ? c.getMemoryPercent() : 0;
        long memMb = c.getMemoryMb() != null ? c.getMemoryMb() : 0;
        String type = switch (c.getType()) {
            case HTTP, NGINX -> "HTTP Server";
            case DATABASE, MYSQL -> "Database";
            case CACHE, REDIS -> "Cache";
            case API -> "HTTP Server";
        };
        return new ContainerDTO(c.getId(), c.getName(), type,
                String.format("%.1f%%", cpu),
                memMb + "MB",
                String.format("%.0f%%", memPct),
                c.getDiskIo(), c.getStatus().name(),
                c.getNetworks() != null ? c.getNetworks() : "");
    }
}
