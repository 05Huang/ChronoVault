package com.chronovault.dto.server;

import com.chronovault.entity.Server;

public record ServerDTO(Long id, String name, String ip, String os, String status, String uptime,
                        Long uptimeSeconds, Integer sshPort, String sshUsername, String sshAuthMethod,
                        boolean autoSnapshotEnabled, Long groupId) {
    public static ServerDTO from(Server s) {
        String uptime = s.getUptimeSeconds() != null ? formatUptime(s.getUptimeSeconds()) : "未知";
        return new ServerDTO(s.getId(), s.getName(), s.getIp(), s.getOs(), s.getStatus().name(), uptime,
                s.getUptimeSeconds(), s.getSshPort(), s.getSshUsername(), s.getSshAuthMethod(),
                s.isAutoSnapshotEnabled(),
                s.getGroup() != null ? s.getGroup().getId() : null);
    }

    private static String formatUptime(long seconds) {
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        return days + " 天 " + hours + " 小时";
    }
}
